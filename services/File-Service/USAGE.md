# File-Service Usage Guide

Tai lieu nay huong dan cach dung File-Service de upload, xac nhan upload va lay URL download/xem file.

## Tong quan

File-Service khong nhan file binary truc tiep qua backend. Luong dung dung la:

1. Goi API `init-upload` de tao metadata trong database va lay presigned upload URL.
2. Frontend/client `PUT` file truc tiep len MinIO bang `uploadUrl`.
3. Goi API `complete` de File-Service kiem tra object tren MinIO va chuyen file sang trang thai `ACTIVE`.
4. Khi can xem/tai file, goi API `download-url`.

Base path khi di qua Api-Gateway:

```text
http://localhost:8080/api/file
```

Base path khi goi truc tiep File-Service:

```text
http://localhost:8101/api/file
```

Swagger:

```text
http://localhost:8080/api/file/swagger-ui.html
http://localhost:8101/api/file/swagger-ui.html
```

## Header bat buoc

File-Service lay user hien tai tu header do Api-Gateway forward xuong.

```http
X-User-ID: 550e8400-e29b-41d4-a716-446655440000
X-User_Roles: USER
X-Request-ID: optional-trace-id
Authorization: Bearer <token>
```

Trong code hien tai, neu thieu `X-User-ID`, service dang fallback ve UUID mac dinh:

```text
550e8400-e29b-41d4-a716-446655440000
```

Khi tich hop that, nen dam bao Gateway/Auth filter luon forward `X-User-ID`.

## FilePurpose

`purpose` quyet dinh file public/private, bucket nao duoc dung va object key duoc tao nhu the nao.

Gia tri hop le:

```text
USER_AVATAR
USER_DOCUMENT
PAYMENT_PROOF
INVOICE
PRODUCT_IMAGE
ITEM_IMAGE
CATEGORY_IMAGE
PROMOTION_IMAGE
BANNER_IMAGE
```

Visibility:

```text
PUBLIC: PRODUCT_IMAGE, ITEM_IMAGE, CATEGORY_IMAGE, PROMOTION_IMAGE, BANNER_IMAGE
PRIVATE: USER_AVATAR, USER_DOCUMENT, PAYMENT_PROOF, INVOICE
```

Bucket:

```text
PUBLIC  -> minio.buckets.public-bucket
PRIVATE -> minio.buckets.private-bucket
```

## Gioi han file

Content type hien duoc chap nhan:

```text
image/png
image/jpeg
application/pdf
```

Kich thuoc toi da hien dang hard-code trong service:

```text
10 MB
```

Ten file duoc dung de lay extension. Extension phai match:

```text
[a-z0-9]{1,10}
```

Vi du hop le: `avatar.png`, `invoice.pdf`, `product.jpeg`.

## Object key

File-Service tu tao `objectKey`, client khong tu truyen object key.

Pattern hien tai:

```text
PRODUCT_IMAGE, ITEM_IMAGE:
items/{refId}/{fileId}.{ext}

CATEGORY_IMAGE:
categories/{refId}/{fileId}.{ext}

PROMOTION_IMAGE, BANNER_IMAGE:
public-assets/{purpose-lowercase}/{fileId}.{ext}

USER_AVATAR:
users/{currentUserId}/avatar/{fileId}.{ext}

USER_DOCUMENT:
users/{currentUserId}/documents/{fileId}.{ext}

PAYMENT_PROOF:
payments/{refId}/proofs/{fileId}.{ext}

INVOICE:
invoices/{fileId}.{ext}
```

Luu y:

```text
PRODUCT_IMAGE, ITEM_IMAGE, CATEGORY_IMAGE bat buoc co refId.
PAYMENT_PROOF nen truyen refId cua payment/order lien quan.
```

## API 1: Init upload

Tao metadata file voi status `PENDING` va tra ve presigned URL de upload len MinIO.

```http
POST /api/file/init-upload
Content-Type: application/json
```

Request body:

```json
{
  "originalFilename": "product.png",
  "contentType": "image/png",
  "sizeBytes": 245123,
  "purpose": "PRODUCT_IMAGE",
  "ownerType": "PRODUCT",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "refType": "PRODUCT",
  "refId": "22222222-2222-2222-2222-222222222222"
}
```

Field:

```text
originalFilename: bat buoc, ten file goc, dung de lay extension.
contentType: bat buoc, phai nam trong danh sach allowed content types.
sizeBytes: bat buoc, so byte cua file, > 0 va <= 10 MB.
purpose: bat buoc, mot trong cac FilePurpose.
ownerType: tuy chon, loai owner nghiep vu, vi du USER, PRODUCT, PAYMENT.
ownerId: tuy chon, UUID cua owner; neu null se dung current user id tu X-User-ID.
refType: tuy chon, loai doi tuong tham chieu, vi du PRODUCT, CATEGORY, PAYMENT.
refId: tuy chon voi mot so purpose, UUID doi tuong tham chieu.
```

Response mau:

```json
{
  "code": 200,
  "message": "init successfully",
  "data": {
    "fileId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "visibility": "PUBLIC",
    "bucket": "hvt-file-public",
    "objectKey": "items/22222222-2222-2222-2222-222222222222/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png",
    "uploadUrl": "http://localhost:9000/hvt-file-public/items/...",
    "publicUrl": "http://localhost:9000/hvt-file-public/items/...",
    "expiresInSeconds": 300
  }
}
```

Voi file private, `publicUrl` se la `null`.

## API 2: Upload file len MinIO

Sau khi co `uploadUrl`, client upload file truc tiep len MinIO bang HTTP `PUT`.

```bash
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/png" \
  --upload-file ./product.png
```

Luu y:

```text
Content-Type nen trung voi contentType da gui o init-upload.
Khong goi backend de upload binary.
uploadUrl co han, mac dinh 300 giay.
```

Vi du JavaScript browser:

```js
await fetch(initResponse.data.uploadUrl, {
  method: "PUT",
  headers: {
    "Content-Type": file.type
  },
  body: file
});
```

## API 3: Complete upload

Sau khi upload len MinIO thanh cong, goi API nay de File-Service kiem tra object ton tai va doi status sang `ACTIVE`.

```http
POST /api/file/{fileId}/complete
```

Response hien tai cua controller:

```json
{
  "code": 200,
  "message": "success",
  "data": "Success"
}
```

Luu y quan trong:

```text
Service layer da co CompleteUploadResponse(fileId, visibility, publicUrl),
nhung controller hien tai van tra ve String "Success".
Neu frontend can publicUrl ngay sau complete, co the dung publicUrl da tra ve tu init-upload hoac goi download-url.
```

## API 4: Get download URL

Lay URL de xem/tai file da `ACTIVE`.

```http
GET /api/file/{fileId}/download-url
```

Response voi file public:

```json
{
  "code": 200,
  "status": "success",
  "data": {
    "url": "http://localhost:9000/hvt-file-public/items/...",
    "expiresInSeconds": null,
    "visibility": "PUBLIC"
  }
}
```

Response voi file private:

```json
{
  "code": 200,
  "status": "success",
  "data": {
    "url": "http://localhost:9000/hvt-file-private/users/...",
    "expiresInSeconds": 300,
    "visibility": "PRIVATE"
  }
}
```

Voi file private, File-Service se kiem tra `ownerId == X-User-ID`. Neu khong khop se bi forbidden.

## Curl end-to-end

Vi du upload anh san pham qua Gateway:

```bash
GATEWAY=http://localhost:8080
USER_ID=550e8400-e29b-41d4-a716-446655440000
PRODUCT_ID=22222222-2222-2222-2222-222222222222

curl -X POST "$GATEWAY/api/file/init-upload" \
  -H "Content-Type: application/json" \
  -H "X-User-ID: $USER_ID" \
  -d '{
    "originalFilename": "product.png",
    "contentType": "image/png",
    "sizeBytes": 245123,
    "purpose": "PRODUCT_IMAGE",
    "ownerType": "PRODUCT",
    "ownerId": "'"$PRODUCT_ID"'",
    "refType": "PRODUCT",
    "refId": "'"$PRODUCT_ID"'"
  }'
```

Lay `uploadUrl` va `fileId` tu response, sau do:

```bash
curl -X PUT "$UPLOAD_URL" \
  -H "Content-Type: image/png" \
  --upload-file ./product.png

curl -X POST "$GATEWAY/api/file/$FILE_ID/complete" \
  -H "X-User-ID: $USER_ID"

curl "$GATEWAY/api/file/$FILE_ID/download-url" \
  -H "X-User-ID: $USER_ID"
```

## Vi du frontend flow

```js
async function uploadFile(file, purpose, refId) {
  const initRes = await fetch("/api/file/init-upload", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      originalFilename: file.name,
      contentType: file.type,
      sizeBytes: file.size,
      purpose,
      refType: refId ? "PRODUCT" : null,
      refId
    })
  }).then(r => r.json());

  const init = initRes.data;

  await fetch(init.uploadUrl, {
    method: "PUT",
    headers: {
      "Content-Type": file.type
    },
    body: file
  });

  await fetch(`/api/file/${init.fileId}/complete`, {
    method: "POST"
  });

  return init.publicUrl ?? init.fileId;
}
```

## Database chinh

Bang chinh:

```text
files
```

Cac cot quan trong:

```text
id: UUID BINARY(16), file id.
bucket: MinIO bucket.
object_key: path object trong MinIO.
public_url: URL public neu visibility PUBLIC.
purpose: muc dich file.
visibility: PUBLIC hoac PRIVATE.
status: PENDING, ACTIVE, DELETED, FAILED.
owner_id / owner_type: owner nghiep vu.
ref_type / ref_id: doi tuong tham chieu.
created_by / updated_by / deleted_by: UUID BINARY(16).
```

## Loi thuong gap

`Failed to resolve 'file-service'`

Gateway dang nhan instance Eureka co hostname `file-service`, nhung ban dang chay local nen may host khong resolve duoc DNS nay. Khi chay local, de `EUREKA_INSTANCE_HOSTNAME` mac dinh `localhost`. Khi chay Docker, moi container co the set:

```text
EUREKA_INSTANCE_HOSTNAME=file-service
```

`File is empty`

`sizeBytes <= 0`.

`File too large`

File lon hon 10 MB.

`Unsupported content type`

`contentType` khong phai `image/png`, `image/jpeg`, hoac `application/pdf`.

`Product or item id is required`

`PRODUCT_IMAGE` hoac `ITEM_IMAGE` can `refId`.

`Category id is required`

`CATEGORY_IMAGE` can `refId`.

`File is not available`

File chua `ACTIVE`, da bi xoa, hoac khong hop le de download.

`Forbidden to access upload resource`

File private nhung `X-User-ID` khong phai owner.

## Cau hinh can co

```yaml
minio:
  endpoint: ${MINIO_ENDPOINT}
  public-endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  buckets:
    public-bucket: hvt-file-public
    private-bucket: hvt-file-private
  presigned-upload-expiry-seconds: 300
  presigned-download-expiry-seconds: 300
```

Neu dung public file that, `public-endpoint` nen la endpoint ma browser/client truy cap duoc.

## Checklist khi tich hop service khac

1. Chon dung `purpose`.
2. Truyen `refId` neu file gan voi product/category/payment.
3. Goi `init-upload`.
4. Upload binary bang `PUT uploadUrl`.
5. Goi `complete`.
6. Luu `fileId` vao entity nghiep vu cua service khac.
7. Khi can hien thi/tai file, goi `download-url`.
