# Deploy box 1 "app"

Box 1 chạy **Api-Gateway + 9 service Java + web POS** trên một EC2 bằng docker compose.

> **Kiến trúc image**: image mang kiến trúc của máy build. Box 1 là EC2 t3 (**amd64**), nên
> image build trên Mac Apple Silicon ra **arm64** và sẽ không chạy. Build trên box 1, trên
> Jenkins (box 3, amd64), hoặc thêm `--platform=linux/amd64` (chậm vì phải giả lập).

> **Không deploy**: Organization-Service (module rỗng, route cũng đã bỏ khỏi gateway) và
> Recommendation-Service (để sau; route `/api/recommendation/**` sẽ trả 503).
Không có Eureka: service gọi nhau qua DNS của compose network, khai tĩnh trong
`application-prod.yml` của từng module.

"Box 1/2/3" là cách gọi trong tài liệu này cho **ba vai trò máy chủ**, không phải thuật
ngữ của AWS:

| | Vai trò | Nội dung | Hiện trạng |
|---|---|---|---|
| **Box 1** | máy APP | Api-Gateway + 9 service Java + pos-web + nginx(host) | Đang chạy: EC2 `ip-172-31-36-154` **m7i-flex.large** (2 vCPU, 8GB, x86_64), private `172.31.36.154`, tailnet `100.109.128.107`, disk 27G |
| **Box 2** | máy HẠ TẦNG | MySQL, Kafka, Redis, MinIO, Keycloak, Kafka Connect, nginx | Đang chạy: EC2 `ip-172-31-42-27`, private `172.31.42.27`, tailnet `100.99.2.30`, public `3.107.34.56` |
| **Box 3** | máy CI | Jenkins + registry | Chưa dựng, làm sau |

Ba vai trò **không bắt buộc là ba máy**. Gộp APP và HẠ TẦNG vào một máy 16GB cũng được —
xem "Chạy tất cả trên một máy" bên dưới.

`auth.hvtra.id.vn` và `storage.hvtra.id.vn` đã có reverse proxy công khai ở box 2, nên
box 1 **không** proxy lại Keycloak hay MinIO.

## Chuẩn bị box 1

```bash
# Docker + compose plugin
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # logout/login lại

# 4GB swap làm phao, không phải để chạy thật
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Security Group box 1: mở 80/443 cho `0.0.0.0/0`; 22 chỉ từ IP của bạn (hoặc dùng SSM và
bỏ 22). **Không** publish 8080–8183 ra ngoài — compose chỉ bind pos-web vào loopback.

Disk: box 1 hiện 27G (còn 24G). Build on-box ăn khoảng 6GB cho image + cache Maven trong
BuildKit, nên còn dư, nhưng đặt cron `docker system prune -af --filter until=168h` hàng
tuần để cache không phình dần.

Security Group box 2: cho SG của box 1 vào 3306 / 19092 / 6379 / 9000. Hai box phải
**cùng VPC, cùng AZ** — khác AZ là trả phí data transfer và cộng latency vào mọi query.
Hiện cả hai đều trong `172.31.32.0/20` của VPC default nên đã cùng subnet, cùng AZ.

## Lần deploy đầu

```bash
git clone <repo> && cd <repo>/deploy
cp .env.example .env && chmod 600 .env
vi .env          # điền giá trị thật, xem ghi chú trong file

docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps        # chờ tất cả healthy
```

Phải chạy **từ thư mục `deploy/`**: compose dùng `.env` ở đây cho cả hai việc — nội suy
`${...}` trong file compose, và nạp biến vào container qua `env_file`.

Build mất khá lâu lần đầu (Maven tải toàn bộ dependency trong container). 11 service dùng
cùng `Dockerfile` nên stage build Maven chỉ chạy **một lần** rồi được cache cho cả 11
image; cache `~/.m2` nằm trong BuildKit cache mount nên lần sau nhanh.

Chưa có DNS cho web POS thì kiểm tra qua loopback:

```bash
ssh -L 3000:127.0.0.1:3000 <box1>   # rồi mở http://localhost:3000
```

## DNS

Box 1 dùng **Elastic IP** + bản ghi `A` thẳng ở Mắt Bão (`ns1/ns2.matbao.vn`), không qua
DuckDNS.

Lý do: từ 02/2024 AWS tính tiền **mọi** IPv4 public đang dùng, kể cả IP tự cấp, khoảng
$0.005/giờ ≈ $3.6/tháng — Elastic IP **cùng giá**, nên DuckDNS không tiết kiệm được gì.
Mà IP tự cấp đổi sau mỗi lần stop/start, và DuckDNS chỉ vá được triệu chứng: vẫn còn một
khoảng vài phút sau mỗi lần khởi động lại mà tên miền trỏ IP cũ. Rơi đúng lúc Let's
Encrypt gia hạn thì cert hỏng.

Lưu ý: Elastic IP **đã cấp phát mà không gắn vào instance nào vẫn bị tính tiền** — nhớ
release khi bỏ máy.

`auth.hvtra.id.vn` và `storage.hvtra.id.vn` của box 2 vẫn đang là CNAME tới
`hvtra-infra.duckdns.org`; khi rảnh nên đổi sang A record thẳng cho bớt một mắt xích.

## Bật TLS bằng nginx

TLS do **nginx ở host** lo, giống box 2 (đang chạy nginx/1.28.3 Ubuntu cho `auth` và
`storage`). `pos-web` chỉ publish `127.0.0.1:3000`, nginx proxy vào đó.

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
sudo cp deploy/nginx/pos.hvtra.id.vn.conf /etc/nginx/sites-available/
sudo ln -s /etc/nginx/sites-available/pos.hvtra.id.vn.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d pos.hvtra.id.vn      # tự sửa các dòng ssl_* trong file
```

Trước đó phải tạo A record `pos.hvtra.id.vn` trỏ về Elastic IP của box 1 — hiện chỉ
`auth` và `storage` tồn tại.

Sau khi có domain, cập nhật Keycloak (realm `hvt`, client `hvtra-pos`): thêm
`https://pos.hvtra.id.vn/*` vào **Valid redirect URIs** và **Valid post logout redirect
URIs**, thêm `https://pos.hvtra.id.vn` vào **Web origins**. Dấu `*` là bắt buộc:
`keycloak-js` dùng `redirect_uri = window.location.href`, tức URL trang hiện tại.
Và đổi `CORS_ORIGIN_1`/`CORS_ORIGIN_2` trong `.env` sang domain đó.

**Không cần sửa `Content-Security-Policy` hay `X-Frame-Options` của realm** — frontend đã
bỏ `silentCheckSsoRedirectUri` nên không còn iframe xuyên origin.

Phương án thay thế: dùng luôn nginx của **box 2** làm reverse proxy chung
(`pos.hvtra.id.vn` → box 2 → `http://<private-ip-box1>:3000`). Tận dụng certbot đã cấu
hình sẵn, nhưng mọi traffic web phải đi thêm một hop qua box 2 và box 2 thành điểm chết
đơn lẻ cho cả web.

## Đưa image lên box 1

Image mang kiến trúc của máy build. Box 1 là EC2 t3 (**amd64**), Mac Apple Silicon ra
**arm64** → image build trên Mac **không chạy** trên box 1.

| Cách | Khi nào dùng | Lưu ý |
|---|---|---|
| **Build trên box 1** (`git pull && docker compose up -d --build`) | Bây giờ, lần deploy đầu | Native amd64, không cần registry, không phải upload GB nào. Build chiếm ~1.3GB RAM + CPU — chấp nhận được khi chưa có traffic |
| Jenkins (box 3, amd64) → registry → box 1 pull | Khi dựng box 3 | ECR cùng region (private, không giới hạn pull) hoặc `registry:2` tự host. Đây là đích cuối |
| Docker Hub từ Mac | Không khuyên | Phải `--platform linux/amd64` (chạy qua giả lập, build rất chậm); 10 image ~500MB, mỗi lần deploy đẩy lại ~1.4GB layer app qua đường upload nhà; Docker Hub free chỉ 1 private repo |
| Đổi box 1 sang **t4g** (Graviton/arm64) | Nếu muốn dùng image build từ Mac | Rẻ hơn ~20%, nhưng phải kiểm image hạ tầng có bản arm64 |

## Chạy tất cả trên một máy

Nếu không muốn tạo máy thứ hai: **nâng máy HẠ TẦNG hiện tại từ 8GB lên 16GB**
(t3.large → t3.xlarge) rồi chạy luôn app trên đó.

RAM: hạ tầng ~6GB + app ~3.4GB (đo lúc idle) = **~9.4GB / 16GB**, còn ~6.6GB cho page
cache của Kafka và lúc có tải. Trên máy 8GB thì **không đủ** — hạ tầng đã chiếm 6GB, chỉ
còn ~2GB, thiếu khoảng 1.4GB và OOM killer thường chọn đúng MySQL hoặc Kafka.

Chi phí t3.xlarge xấp xỉ 2× t3.large, tức **bằng đúng việc chạy hai máy t3.large** — không
đắt hơn, mà ít việc hơn nhiều:

- nginx + certbot đã có sẵn trên máy đó, chỉ cần thêm vhost `pos.hvtra.id.vn`
- DNS đã trỏ về đúng IP đó (`hvtra-infra.duckdns.org`), chỉ thêm một bản ghi cho `pos`
- Không cần Security Group liên máy, không cần Tailscale giữa hai máy, không có hop mạng
  nào thêm giữa app và DB

**Trước khi nâng, kiểm Elastic IP.** Đổi instance type phải `stop` máy. Nếu public IP
`3.107.34.56` **không phải** Elastic IP thì sau khi start lại nó sẽ đổi, và
`auth.hvtra.id.vn` + `storage.hvtra.id.vn` đứt cho tới khi cập nhật duckdns. Xem ở EC2
console → Elastic IPs. Chưa có thì cấp phát và gán trước khi stop.

Cấu hình `.env` khi dùng một máy: vẫn để `MYSQL_HOST=172.31.42.27` (private IP của chính
máy đó), **không** dùng `127.0.0.1` — trong container thì `127.0.0.1` là loopback của
chính container, không phải của host. Hạ tầng đang publish port ra host (3306/19092/6379/
9000 truy cập được từ ngoài qua tailnet) nên đi bằng private IP là chạy.

Đánh đổi: một máy chết là mất cả hệ thống, và lúc `--build` thì Maven giành CPU/RAM với
service đang chạy. Khi nào cần thì tách APP ra máy riêng — cấu hình không phải đổi gì
ngoài mấy biến host trong `.env`.

## Kiểm tra

```bash
# Health từng service
docker compose -f docker-compose.prod.yml ps
for p in 8080 8081 8082 8084 8085 8086 8087 8088 8101 8183; do
  echo -n "$p: "; docker compose -f docker-compose.prod.yml exec -T api-gateway \
    wget -qO- http://127.0.0.1:$p/actuator/health 2>/dev/null || echo loi
done

# RAM thuc te -> dung so nay de chot lai mem_limit
docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
```

`mem_limit` trong compose **đã đo** bằng `docker stats` trên stack thật (arm64/macOS, lúc
idle, 2026-11-04), không phải ước lượng:

| Service | Dùng thật | `mem_limit` | % |
|---|---|---|---|
| api-gateway | 257 MiB | 448m | 57% |
| catalog-service | 367 MiB | 576m | 64% |
| customer-service | 426 MiB | 640m | 67% |
| inventory-service | 429 MiB | 704m | 61% |
| order-service | 391 MiB | 704m | 56% |
| identity-service | 322 MiB | 512m | 63% |
| file-service | 314 MiB | 512m | 61% |
| promotion-service | 367 MiB | 576m | 64% |
| notification-service | 279 MiB | 512m | 54% |
| payment-service | 276 MiB | 512m | 54% |
| pos-web | 16 MiB | 96m | 17% |

Tổng dùng thật lúc idle **~3.4GB**, trần cap ~5.7GB trên máy 8GB. Đo lại trên box 1
(amd64) khi có tải thật — con số trên là lúc không có request nào.

## Cập nhật

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
docker image prune -f
```

Khi có Jenkins (bước 7), đổi sang build ở box 3 → push registry → box 1 chỉ
`docker compose pull && up -d`, để không phải chạy Maven trên máy đang serve.

## Khác biệt giữa smoke test ở local và box 1

`deploy/.env` dùng để test ở local có **công tắc an toàn** phải đổi khi lên box 1:

| Biến | Local | Box 1 |
|---|---|---|
| `MYSQL_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `REDIS_HOST` | `100.99.2.30` (tailnet) | `172.31.42.27` (private VPC) |
| `KAFKA_ENABLED`, `KAFKA_CONFIG_ENABLED` | `false` | `true` |
| `ORDER_RETURN_INVENTORY_RECONCILIATION_ENABLED` | `false` | `true` |
| `FLYWAY_ENABLED` | `false` | `true` |
| `KAFKA_GROUP_PREFIX` | `smoketest-` | `prod-` |

Lý do tắt Kafka khi test ở local: consumer group mới + `auto-offset-reset: earliest` sẽ
**đọc lại toàn bộ lịch sử topic và xử lý lại** vào DB thật — trừ kho, gửi thông báo, cập
nhật đơn. Order-Service còn có scheduler tự "recovery" đơn mỗi 60s. Cả hai đều ghi vào
DB dùng chung, nên smoke test phải tắt.

## Lỗi hay gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| Service chết ngay, log `Could not resolve placeholder` | Thiếu biến trong `.env`. Đây là **cố ý**: `application-prod.yml` không để default cho giá trị hạ tầng, để không bao giờ âm thầm nối sai chỗ |
| `Communications link failure` | SG box 2 chưa cho SG box 1 vào 3306, hoặc `MYSQL_HOST` sai |
| Gateway trả 503 cho một route | Service đó chưa healthy, hoặc route `lb://` mới thêm mà quên khai trong `simple.instances` của `application-prod.yml` — `SimpleDiscoveryInstancesTest` chặn trường hợp này |
| `/api/recommendation/ai-reports/**` trả 503 | Recommendation-Service chưa deploy, đúng như thiết kế hiện tại |
| Container bị OOM-kill | `JAVA_TOOL_OPTIONS` thiếu `MaxRAMPercentage` → JVM lấy 25% RAM **của máy** (2GB/service) |
| Ảnh/file private trả 403 | `MINIO_ENDPOINT` phải là URL công khai: presigned URL do **trình duyệt** mở và chữ ký gắn với host trong URL |
| Startup rất lâu, CPU 100% | t3 hết CPU credit. Bật T3 Unlimited, hoặc để `depends_on` giãn thứ tự khởi động |

## Chưa làm

- Box 3 (Jenkins) + registry + pipeline — bước 7.
- Recommendation-Service: có `Dockerfile` riêng, chưa đưa vào compose. Thêm ~700MB.
- Debezium connector trên box 2 đang `snapshot.mode: initial`; nếu register lại sẽ phát
  lại toàn bộ `outbox_events` lịch sử. Đổi sang `schema_only` trước khi chạy
  `DB_HOST=<ten container mysql> bash infra/debezium/register-connectors.sh`.
- Rotate 3 secret đã vào git history: `INTERNAL_HEADER_SECRET`, password Redis, password
  user `debezium`.
