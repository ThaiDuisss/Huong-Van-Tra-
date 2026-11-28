---
name: drawio-swimlane
description: >-
  Use this skill when generating or editing Swimlane Activity Diagrams in draw.io XML (.drawio)
  format. Enforces monochrome black-and-white styling, concise English labels without technical
  status-setting phrasing, compact vertical height, wide System lane, clear horizontal spacing,
  and complete orthogonal arrows.
---

# Draw.io Swimlane Diagram Skill

Hướng dẫn chuẩn để tạo hoặc chỉnh sửa biểu đồ Swimlane định dạng **draw.io XML (`.drawio`)** theo phong cách trang mẫu *"Create Goods Receipt from Supplier"*, tối ưu độ gọn, rõ ràng và tính đầy đủ của luồng.

---

## 1. Quy tắc Ngôn ngữ & Nội dung (Bắt buộc)

1. **100% Tiếng Anh**, câu chữ **ngắn gọn nhất có thể** (ưu tiên 2–5 từ mỗi ô, tối đa 2 dòng ngắn).
2. **KHÔNG viết kiểu "set trạng thái" / kỹ thuật hóa** trong các ô hành động:
   - **Tránh:** `"Set receipt as DRAFT"`, `"Set receipt as PENDING APPROVAL"`, `"Set receipt as APPROVED"`, `"Set status to POSTED"`, `"Update order status: Completed"`.
   - **Dùng động từ nghiệp vụ trực tiếp:**
     - `"Create draft receipt"` / `"Save draft"`
     - `"Submit for approval"`
     - `"Approve receipt"` / `"Reject receipt"`
     - `"Complete receipt"` / `"Post stock"`
     - `"Validate header"` / `"Validate items"`
     - `"Inspect & enter batches"`
3. **Nhãn nhánh điều kiện (Decision):** Ngắn gọn (`Yes` / `No`, hoặc từ khóa 1–2 từ).

---

## 2. Phong cách Trực quan (Đen trắng, không màu mè)

- **Tuyệt đối đơn sắc đen–trắng**, không dùng màu nền pastel, không dùng `shadow=1`, không dùng gradient.
- **Font:** `Helvetica` hoặc `Arial`, cỡ chữ **`20`** (`fontSize=20` cho toàn bộ Title, Lane Header, Node, Decision và nhãn mũi tên), in đậm (`fontStyle=1`), màu chữ `#000000`.
- **Style chuẩn cho từng thành phần:**

| Thành phần | Style draw.io XML |
|---|---|
| **Title Banner** | `rounded=0;whiteSpace=wrap;html=1;fontStyle=1;fontSize=20;align=center;verticalAlign=middle;fillColor=#ffffff;strokeColor=#000000;strokeWidth=1.5;` |
| **Lane Header** | `rounded=0;whiteSpace=wrap;html=1;fontStyle=1;fontSize=20;align=center;verticalAlign=middle;fillColor=#ffffff;strokeColor=#000000;strokeWidth=1.5;` |
| **Lane Body** | `rounded=0;whiteSpace=wrap;html=1;fillColor=none;strokeColor=#333333;strokeWidth=1.2;` |
| **Action (Rectangle)** | `rounded=0;whiteSpace=wrap;html=1;fontSize=20;fontStyle=1;align=center;verticalAlign=middle;fillColor=#ffffff;strokeColor=#000000;strokeWidth=1.4;` |
| **Decision (Rhombus)** | `rhombus;whiteSpace=wrap;html=1;fontSize=20;fontStyle=1;align=center;verticalAlign=middle;fillColor=#ffffff;strokeColor=#000000;strokeWidth=1.4;` |
| **Start Node** | `ellipse;html=1;fillColor=#000000;strokeColor=#000000;` (`30x30`) |
| **End Node** | `ellipse;html=1;shape=endState;fillColor=#000000;strokeColor=#000000;` (`30x30`) |
| **Arrow (Edge)** | `edgeStyle=orthogonalEdgeStyle;rounded=0;orthogonalLoop=1;jettySize=auto;html=1;endArrow=block;endFill=1;endSize=8;strokeColor=#000000;strokeWidth=1.4;fontSize=20;fontStyle=1;fontColor=#000000;labelBackgroundColor=#ffffff;` |

---

## 3. Quy tắc Bố cục & Kích thước (Tránh quá dài dọc, quá hẹp ngang)

1. **Chiều ngang đủ rộng, Lane `System` phải rộng:**
   - Lane Actor (`Staff`, `Manager`, `QC Staff`, `Customer`...): `width` tối thiểu `260`–`320px`.
   - **Lane `System`**: `width` tối thiểu `620`–`780px` để bố trí được **2–3 cột song song** (ví dụ: cột trái hiện lỗi validation / nhánh `No`, cột giữa xử lý chính, cột phải nhánh ngoại lệ / rẽ nhánh).
   - Khoảng cách ngang tối thiểu giữa 2 node cạnh nhau: **`60px`–`90px`** để mũi tên và nhãn `Yes`/`No` hiển thị rõ ràng, không bị dính sát vào viền ô.

2. **Không quá dài theo chiều dọc:**
   - Tận dụng bề ngang của lane `System` và đặt các bước liên hoàn cùng tầng **trên cùng hàng ngang** (left-to-right / right-to-left) thay vì xếp dọc từng ô một.
   - Chiều cao ô Action tiêu chuẩn: `56`–`70px` (rộng `170`–`220px`); ô Decision: `90`–`110px` (rộng `160`–`200px`).
   - Khoảng cách dọc giữa các hàng node: **`45px`–`65px`** (vừa đủ thấy rõ mũi tên, không kéo dài thừa).
   - Chiều cao của tất cả Lane Body phải bằng nhau và bao trọn node cuối cùng + `40px` padding đáy.

3. **Cấu trúc tọa độ:**
   - Title banner nằm trên cùng (`height=45`, `width` = tổng chiều rộng các lane).
   - Ngay dưới Title là các Lane Header (`height=45`, `y = titleY + 45`), và ngay dưới mỗi Header là Lane Body (`y = headerY + 45`).
   - Đặt toàn bộ node và edge ở `parent="1"` với tọa độ tuyệt đối để tránh lỗi lệch đường nối khi bẻ góc qua nhiều lane.

---

## 4. Quy tắc Mũi tên & Kiểm tra Bắt buộc

1. **Phải thấy rõ mũi tên:**
   - Luôn dùng `edgeStyle=orthogonalEdgeStyle;endArrow=block;endFill=1;endSize=8;strokeWidth=1.4;`.
   - Khoảng hở nối giữa 2 ô tối thiểu `45px` để đầu mũi tên không bị che hoặc quá ngắn.
   - Với các đường quay vòng (ví dụ: lỗi validation quay lại bước nhập liệu) hoặc đường đi ngang qua lane khác, khai báo rõ `<Array as="points"><mxPoint x="..." y="..." /></Array>` đi theo hành lang trống giữa các hàng/cột, **không cắt ngang qua chữ hoặc qua ô khác**.
   - Dùng `labelBackgroundColor=#ffffff` cho nhãn `Yes`/`No` trên mũi tên để đường kẻ không cắt ngang chữ.

2. **Không được thiếu mũi tên nối (Checklist trước khi trả kết quả):**
   - Mọi `edge="1"` phải có đủ `source` và `target` trỏ đúng `id` của node tồn tại.
   - Mọi node (trừ `Start`) phải có **ít nhất 1 mũi tên đi vào**.
   - Mọi node (trừ `End`) phải có **ít nhất 1 mũi tên đi ra**.
   - Mọi node `Decision` (`rhombus`) phải có **đủ các nhánh đầu ra** (thường là 2 nhánh `Yes` và `No`), mỗi nhánh đều có nhãn rõ ràng.
   - Kiểm tra lại XML hợp lệ (`<mxfile>` -> `<diagram>` -> `<mxGraphModel>` -> `<root>`).
