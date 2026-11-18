import openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from copy import copy

def build_inventory_it_report():
    file_path = ".local/doc/Report-5.2_Integration Test.xlsx"
    wb = openpyxl.load_workbook(file_path)

    # Common Styles
    font_name = "Tahoma"
    font_size = 10.0

    font_regular = Font(name=font_name, size=font_size, bold=False)
    font_bold = Font(name=font_name, size=font_size, bold=True)
    font_header = Font(name=font_name, size=font_size, bold=True, color="00FFFFFF")
    font_passed = Font(name=font_name, size=font_size, bold=False, color="00006100")
    font_pending = Font(name=font_name, size=font_size, bold=False, color="009C6500")

    fill_header = PatternFill(fill_type="solid", fgColor="0076923C", bgColor="0076923C")
    fill_func = PatternFill(fill_type="solid", fgColor="00CCFFFF", bgColor="00CCFFFF")
    fill_passed = PatternFill(fill_type="solid", fgColor="00C6EFCE", bgColor="00C6EFCE")
    fill_pending = PatternFill(fill_type="solid", fgColor="00FFEB9C", bgColor="00FFEB9C")
    fill_meta_lbl = PatternFill(fill_type="solid", fgColor="00F2F2F2", bgColor="00F2F2F2")

    thin_border_side = Side(border_style="thin", color="00D9D9D9")
    thin_border = Border(left=thin_border_side, right=thin_border_side, top=thin_border_side, bottom=thin_border_side)

    align_left = Alignment(horizontal="left", vertical="top", wrap_text=True)
    align_center = Alignment(horizontal="center", vertical="center", wrap_text=True)
    align_center_top = Alignment(horizontal="center", vertical="top", wrap_text=True)

    col_widths = {
        "A": 17.0, "B": 34.5, "C": 35.0, "D": 35.0, "E": 28.0,
        "F": 9.5,  "G": 11.0, "H": 8.0,  "I": 9.5,  "J": 11.0,
        "K": 8.0,  "L": 9.5,  "M": 11.0, "N": 8.0
    }

    # Data for the 5 inventory features
    features_data = [
        {
            "sheet_name": "Location_Management",
            "feature_title": "Quản lý Vị trí kho & Kệ bán hàng POS (Location & Store Shelf Management)",
            "test_req": "Kiểm thử tích hợp vòng đời điểm lưu kho và kệ trưng bày POS: tạo kho lưu trữ (WAREHOUSE, STORE, DISPOSAL), phân cấp kho-kệ (Location - StoreShelf), cập nhật thông tin kho và sức chứa kệ (capacity), kích hoạt/ngừng hoạt động, tra cứu phân cấp hierarchy, tra cứu lookup phục vụ các màn hình nhập kho/chuyển kệ, và kiểm tra tính toàn vẹn ràng buộc khi xóa/vô hiệu hóa.",
            "functions": [
                {
                    "name": "Function 1: Tạo mới vị trí kho lưu trữ (Create Inventory Location)",
                    "cases": [
                        {
                            "id": "ITC_LOC_001",
                            "desc": "Tạo kho tổng (WAREHOUSE) mới thuộc chi nhánh với dữ liệu hợp lệ",
                            "proc": "1. Go to Inventory Locations page (/inventory/locations)\n2. Click \"Thêm kho hàng\" button\n3. Select Branch: \"Chi nhánh Hòa Lạc\" (id=\"00000000-0000-0000-0000-000000000001\")\n4. Enter Location Name: \"Kho Tổng Hương Vân Trà\"\n5. Select Location Type: \"WAREHOUSE\"\n6. Enter Address: \"Khu Công Nghệ Cao, Hòa Lạc, Hà Nội\"\n7. Select Status: \"ACTIVE\"\n8. Click \"Lưu\" button",
                            "expected": "1. Direct Result: System returns 201 Created and persists InventoryLocationResponse:\n   - id=\"660e8400-e29b-41d4-a716-446655440001\" (Regex: ^[0-9a-fA-F-]{36}$)\n   - name=\"Kho Tổng Hương Vân Trà\", locationType=\"WAREHOUSE\", status=\"ACTIVE\"\n   - Database: New record created in inventory_locations table with is_deleted=false.\n2. Cross-functional Impact:\n   - Location List page: Instantly displays \"Kho Tổng Hương Vân Trà\" at top.\n   - Goods Receipt creation: Dropdown \"Kho đích\" displays this warehouse.\n   - Location Lookup API (/api/inventory/inventory-locations/lookup): Returns this warehouse.",
                            "pre": "Tài khoản có quyền ROLE_WAREHOUSE_MANAGER, chi nhánh Hòa Lạc đã tồn tại trong DB."
                        },
                        {
                            "id": "ITC_LOC_002",
                            "desc": "Tạo kho cửa hàng bán lẻ (STORE) liên kết quầy POS",
                            "proc": "1. Go to Inventory Locations page\n2. Click \"Thêm kho hàng\" button\n3. Select Branch: \"Chi nhánh Hòa Lạc\"\n4. Enter Location Name: \"Kho Cửa Hàng Bán Lẻ Hòa Lạc\"\n5. Select Location Type: \"STORE\"\n6. Enter Address: \"Số 1 Cửa Hàng Thôn 3, Hòa Lạc\"\n7. Select Status: \"ACTIVE\"\n8. Click \"Lưu\" button",
                            "expected": "1. Direct Result: System returns 201 Created with locationType=\"STORE\" and parent branch linked. Database records store location.\n2. Cross-functional Impact:\n   - Shelf Management page (/inventory/shelves): Dropdown \"Cửa hàng\" allows creating shelves under this store.\n   - Shelf Replenishment: Can select this store as destination store.",
                            "pre": "Chi nhánh hợp lệ, quyền Quản lý kho."
                        },
                        {
                            "id": "ITC_LOC_003",
                            "desc": "Tạo kho với tên bị trùng lặp trong cùng một chi nhánh",
                            "proc": "1. Go to Inventory Locations page\n2. Click \"Thêm kho hàng\" button\n3. Select Branch: \"Chi nhánh Hòa Lạc\"\n4. Enter Location Name: \"Kho Tổng Hương Vân Trà\" (đã tồn tại)\n5. Select Location Type: \"WAREHOUSE\"\n6. Click \"Lưu\" button",
                            "expected": "1. Direct Result: System returns 400 Bad Request with error: \"Tên kho hàng đã tồn tại trong chi nhánh này\".\n2. Cross-functional Impact:\n   - Form displays validation toast error.\n   - Database transaction rolled back; no duplicate record inserted.",
                            "pre": "Kho \"Kho Tổng Hương Vân Trà\" đã tồn tại trong chi nhánh."
                        }
                    ]
                },
                {
                    "name": "Function 2: Cập nhật thông tin & Trạng thái kho (Update Location & Status)",
                    "cases": [
                        {
                            "id": "ITC_LOC_004",
                            "desc": "Cập nhật tên kho, địa chỉ và thông tin liên hệ thành công",
                            "proc": "1. Go to Inventory Locations page\n2. Click \"Chỉnh sửa\" on location id=\"660e8400-e29b-41d4-a716-446655440001\"\n3. Update Location Name: \"Kho Tổng Hương Vân Trà - Nhà Kho A\"\n4. Update Address: \"Lô E2a-7, Đường D1, Khu CNC Hòa Lạc\"\n5. Click \"Cập nhật\" button",
                            "expected": "1. Direct Result: System returns 200 OK with updated name and address. Database row updated.\n2. Cross-functional Impact:\n   - Location table immediately shows updated name and address.\n   - Warehouse Inventory view (/inventory/warehouse) displays new warehouse name in breadcrumb.",
                            "pre": "Kho id=\"660e8400-e29b-41d4-a716-446655440001\" tồn tại và đang ACTIVE."
                        },
                        {
                            "id": "ITC_LOC_005",
                            "desc": "Chuyển trạng thái kho sang INACTIVE và kiểm tra ảnh hưởng",
                            "proc": "1. Go to Inventory Locations page\n2. Click toggle status switch on location id=\"660e8400-e29b-41d4-a716-446655440001\" to INACTIVE\n3. Confirm dialog: \"Bạn có chắc muốn ngừng hoạt động kho này?\"\n4. Click \"Xác nhận\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"INACTIVE\". Database updates status column.\n2. Cross-functional Impact:\n   - Goods Receipt creation: Dropdown \"Kho đích\" filters out this inactive warehouse.\n   - Shelf Replenishment: Cannot select this warehouse as replenishment source.",
                            "pre": "Kho tồn tại, không có giao dịch chuyển kho đang pending dở dang."
                        }
                    ]
                },
                {
                    "name": "Function 3: Quản lý Kệ bán hàng POS (Store Shelf Management)",
                    "cases": [
                        {
                            "id": "ITC_LOC_006",
                            "desc": "Tạo mới kệ trưng bày POS thuộc kho cửa hàng với sức chứa hợp lệ",
                            "proc": "1. Go to Store Shelves page (/inventory/shelves)\n2. Click \"Thêm kệ hàng\" button\n3. Select Store: \"Kho Cửa Hàng Bán Lẻ Hòa Lạc\" (id=\"660e8400-e29b-41d4-a716-446655440002\")\n4. Enter Shelf Code: \"SHELF-HL-A01\"\n5. Enter Shelf Name: \"Kệ Trà Ô Long A01\"\n6. Enter Capacity: 150 (hộp)\n7. Enter Notes: \"Kệ trung tâm quầy thanh toán\"\n8. Click \"Lưu\" button",
                            "expected": "1. Direct Result: System returns 201 Created and persists StoreShelfResponse:\n   - id=\"770e8400-e29b-41d4-a716-446655440001\" (Regex: ^[0-9a-fA-F-]{36}$)\n   - shelfCode=\"SHELF-HL-A01\", capacity=150, currentStock=0\n   - Database: New record created in store_shelves table.\n2. Cross-functional Impact:\n   - Shelf list table displays new shelf with 0/150 capacity.\n   - Shelf Replenishment page: Can select this shelf as target shelf.",
                            "pre": "Kho cửa hàng id=\"660e8400-e29b-41d4-a716-446655440002\" đang ACTIVE."
                        },
                        {
                            "id": "ITC_LOC_007",
                            "desc": "Cập nhật sức chứa kệ và ghi chú vị trí trong quầy",
                            "proc": "1. Go to Store Shelves page\n2. Click \"Sửa\" on shelf id=\"770e8400-e29b-41d4-a716-446655440001\"\n3. Update Capacity: 200 (hộp)\n4. Update Notes: \"Kệ mở rộng thêm tầng 3\"\n5. Click \"Cập nhật\" button",
                            "expected": "1. Direct Result: System returns 200 OK with capacity=200. Database capacity column updated.\n2. Cross-functional Impact:\n   - Shelf table reflects new capacity 0/200.\n   - Shelf replenishment validation allows transferring up to 200 units.",
                            "pre": "Kệ hàng SHELF-HL-A01 đã tồn tại trong DB."
                        },
                        {
                            "id": "ITC_LOC_008",
                            "desc": "Tạo kệ với mã kệ đã tồn tại trong cùng cửa hàng",
                            "proc": "1. Go to Store Shelves page\n2. Click \"Thêm kệ hàng\" button\n3. Select Store: \"Kho Cửa Hàng Bán Lẻ Hòa Lạc\"\n4. Enter Shelf Code: \"SHELF-HL-A01\" (đã tồn tại)\n5. Enter Capacity: 100\n6. Click \"Lưu\" button",
                            "expected": "1. Direct Result: System returns 400 Bad Request with error: \"Mã kệ đã tồn tại trong cửa hàng này\".\n2. Cross-functional Impact:\n   - System displays toast validation error.\n   - Database transaction rolled back; no duplicate shelf created.",
                            "pre": "Kệ \"SHELF-HL-A01\" đã tồn tại trong cửa hàng."
                        }
                    ]
                },
                {
                    "name": "Function 4: Tra cứu & Phân cấp kho bãi (Location Lookup & Hierarchy)",
                    "cases": [
                        {
                            "id": "ITC_LOC_009",
                            "desc": "Tra cứu nhanh API lookup vị trí kho cho các dropdown giao diện",
                            "proc": "(Using api to simulate in testing)\n1. Send HTTP GET to /api/inventory/inventory-locations/lookup?search=Hòa Lạc\n2. Header: Authorization Bearer Token\n3. Verify HTTP response status and payload schema",
                            "expected": "1. Direct Result: System returns 200 OK with PageResponse<LookupResponse> containing lightweight objects (id, code, name).\n2. Cross-functional Impact:\n   - Goods receipt, production order, and replenishment modal dropdowns load fast with minimal payload.",
                            "pre": "Database đã có ít nhất 2 kho lưu trữ đang ACTIVE."
                        },
                        {
                            "id": "ITC_LOC_010",
                            "desc": "Lấy cây phân cấp kho và kệ bán hàng qua API hierarchy",
                            "proc": "(Using api to simulate in testing)\n1. Send HTTP GET to /api/inventory/inventory-locations/hierarchy?branchId=00000000-0000-0000-0000-000000000001\n2. Header: Authorization Bearer Token\n3. Verify hierarchical tree structure",
                            "expected": "1. Direct Result: System returns 200 OK with List<InventoryLocationHierarchyResponse>:\n   - Store locations contain nested children list of store_shelves.\n2. Cross-functional Impact:\n   - Tree-view navigation in warehouse management displays complete location-to-shelf hierarchy.",
                            "pre": "Kho cửa hàng và các kệ con liên kết đã tồn tại trong DB."
                        }
                    ]
                }
            ]
        },
        {
            "sheet_name": "GoodsReceipt_Management",
            "feature_title": "Quản lý Nhập kho & Lô hàng HSD (Goods Receipt & Batch Management)",
            "test_req": "Kiểm thử tích hợp chu trình nhập kho từ nhà cung cấp: tạo phiếu nhập nháp (Draft), cập nhật dòng hàng/giá nhập/HSD, gửi duyệt (Submit), phê duyệt (Approve) và hoàn tất nhập kho (Post), tự động sinh lô hàng mới (InventoryBatch), cập nhật số lượng tồn kho tức thời (inventory_balances) và ghi sổ giao dịch INBOUND_RECEIPT; kiểm tra từ chối duyệt (Reject), hủy phiếu (Cancel) và nhập kho hàng loạt qua file Excel.",
            "functions": [
                {
                    "name": "Function 1: Lập phiếu nhập kho nháp (Create Draft Goods Receipt)",
                    "cases": [
                        {
                            "id": "ITC_RCP_001",
                            "desc": "Tạo phiếu nhập kho nháp từ NCC với nhiều mặt hàng, đơn giá và HSD",
                            "proc": "1. Go to Goods Receipts page (/inventory/receipts)\n2. Click \"Tạo phiếu nhập kho\" button (/inventory/receipts/new)\n3. Select Destination Warehouse: \"Kho Tổng Hương Vân Trà\"\n4. Select Supplier: \"Công ty Cổ phần Trà Cầu Tre\" (NCC-00001)\n5. Add Line 1: Select Ingredient \"Trà Búp Tươi Tân Cương\" (ING-00001), Qty=500 (kg), Unit Price=120,000, Expiry Date=\"2027-11-01\"\n6. Add Line 2: Select Packaging \"Hộp Thiếc Cao Cấp 200g\" (ING-00002), Qty=1000 (cái), Unit Price=15,000\n7. Click \"Lưu nháp\" button",
                            "expected": "1. Direct Result: System returns 201 Created and persists GoodsReceiptResponse:\n   - id=\"880e8400-e29b-41d4-a716-446655440001\" (Regex: ^[0-9a-fA-F-]{36}$)\n   - receiptNumber=\"GR-202611-00001\" (Regex: ^GR-\\d{6}-\\d{5}$), status=\"DRAFT\"\n   - Total amount=75,000,000 VND, 2 lines created in goods_receipt_lines.\n2. Cross-functional Impact:\n   - Goods Receipts list displays new draft receipt with yellow badge \"Bản nháp\".\n   - Inventory balances NOT yet increased (inventory intact until approved/posted).",
                            "pre": "Nhà cung cấp NCC-00001 và nguyên liệu ING-00001, ING-00002 đã tồn tại."
                        },
                        {
                            "id": "ITC_RCP_002",
                            "desc": "Tạo phiếu nhập kho với kho đích không hợp lệ hoặc bị vô hiệu hóa",
                            "proc": "1. Go to Goods Receipts page\n2. Click \"Tạo phiếu nhập kho\"\n3. Enter invalid/inactive warehouse ID=\"660e8400-e29b-41d4-a716-000000000000\"\n4. Add 1 line with Qty=10\n5. Click \"Lưu nháp\"",
                            "expected": "1. Direct Result: System returns 400 Bad Request / 404 Not Found: \"Kho lưu trữ không tồn tại hoặc đã ngừng hoạt động\".\n2. Cross-functional Impact:\n   - Toast error displayed; transaction rollback ensures no orphan receipt record.",
                            "pre": "Kho đích không tồn tại trong DB."
                        }
                    ]
                },
                {
                    "name": "Function 2: Cập nhật & Gửi duyệt phiếu nhập kho (Update & Submit Goods Receipt)",
                    "cases": [
                        {
                            "id": "ITC_RCP_003",
                            "desc": "Chỉnh sửa số lượng, đơn giá và bổ sung mặt hàng trong phiếu nhập DRAFT",
                            "proc": "1. Go to Goods Receipts page\n2. Open draft receipt \"GR-202611-00001\" for edit (/inventory/receipts/:id/edit)\n3. Update Line 1 Qty from 500 to 600 (kg)\n4. Update Line 1 Unit Price from 120,000 to 118,000 VND\n5. Click \"Cập nhật\" button",
                            "expected": "1. Direct Result: System returns 200 OK. Receipt updated with new total amount=85,800,000 VND. Status remains DRAFT.\n2. Cross-functional Impact:\n   - Receipt details screen reflects updated quantities and recalculates total invoice amount.",
                            "pre": "Phiếu GR-202611-00001 đang ở trạng thái DRAFT."
                        },
                        {
                            "id": "ITC_RCP_004",
                            "desc": "Gửi duyệt phiếu nhập kho (Submit) chuyển trạng thái sang SUBMITTED",
                            "proc": "1. On draft receipt \"GR-202611-00001\" details page\n2. Click \"Gửi duyệt\" button\n3. Confirm dialog: \"Gửi phiếu nhập kho này cho Quản lý phê duyệt?\"\n4. Click \"Xác nhận\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"SUBMITTED\". Database updates status column.\n2. Cross-functional Impact:\n   - Receipt badge turns to blue \"Chờ duyệt\".\n   - Warehouse Manager notification queue receives approval request.",
                            "pre": "Phiếu nhập đang ở trạng thái DRAFT với ít nhất 1 line hàng hợp lệ."
                        }
                    ]
                },
                {
                    "name": "Function 3: Phê duyệt & Đóng dấu nhập kho (Approve, QC & Post Goods Receipt)",
                    "cases": [
                        {
                            "id": "ITC_RCP_005",
                            "desc": "Quản lý duyệt phiếu nhập và Post nhập kho $\\rightarrow$ sinh Lô hàng & cộng tồn kho",
                            "proc": "1. Manager logs in with ROLE_WAREHOUSE_MANAGER\n2. Open submitted receipt \"GR-202611-00001\"\n3. Click \"Phê duyệt & Nhập kho\" (Post Receipt)\n4. Enter actual received quantities and confirm Inspection QC Passed\n5. Click \"Xác nhận nhập kho\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"COMPLETED\" / \"POSTED\":\n   - Generates new InventoryBatch records for received lines with batch numbers and FEFO expiry date.\n   - Database: inventory_balances table increments quantity_on_hand by +600 for ING-00001.\n   - Database: inventory_transactions records transactionType=\"INBOUND_RECEIPT\".\n2. Cross-functional Impact:\n   - Warehouse Inventory view (/inventory/warehouse) immediately shows +600kg on-hand.\n   - Batch drawer displays new batch with expiry date 2027-11-01.\n   - Receipt status badge turns green \"Đã nhập kho\".",
                            "pre": "Phiếu nhập ở trạng thái SUBMITTED, tài khoản có quyền Quản lý kho."
                        },
                        {
                            "id": "ITC_RCP_006",
                            "desc": "Quản lý từ chối duyệt phiếu nhập kèm lý do từ chối",
                            "proc": "1. Manager opens submitted receipt\n2. Click \"Từ chối duyệt\" button\n3. Enter Rejection Reason: \"Sai lệch đơn giá so với hợp đồng nguyên tắc NCC\"\n4. Click \"Xác nhận từ chối\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"REJECTED\" and stores rejectionNotes.\n2. Cross-functional Impact:\n   - Receipt badge turns red \"Bị từ chối\".\n   - Inventory balances remain unchanged; no batches created.",
                            "pre": "Phiếu nhập ở trạng thái SUBMITTED."
                        }
                    ]
                },
                {
                    "name": "Function 4: Hủy phiếu nhập kho (Cancel Goods Receipt)",
                    "cases": [
                        {
                            "id": "ITC_RCP_007",
                            "desc": "Hủy phiếu nhập kho khi chưa Post thành công",
                            "proc": "1. Open draft or rejected receipt\n2. Click \"Hủy phiếu\" button\n3. Enter cancellation reason: \"Nhà cung cấp hết hàng hủy đơn\"\n4. Confirm cancellation",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"CANCELLED\".\n2. Cross-functional Impact:\n   - Receipt marked as CANCELLED, locked from further edits.\n   - Zero impact on warehouse balances.",
                            "pre": "Phiếu nhập ở trạng thái DRAFT hoặc REJECTED."
                        }
                    ]
                },
                {
                    "name": "Function 5: Nhập kho hàng loạt qua file Excel (Goods Receipt Excel Import)",
                    "cases": [
                        {
                            "id": "ITC_RCP_008",
                            "desc": "Tải file mẫu Excel, điền dữ liệu nhập kho và import thành công",
                            "proc": "1. Go to Goods Receipt Import page (/inventory/receipts/import)\n2. Click \"Tải file mẫu\"\n3. Fill Excel template with 10 line items (Item Code, Batch Code, Expiry Date, Qty, Unit Price)\n4. Upload completed Excel file\n5. Review parsed preview grid (0 validation errors)\n6. Click \"Lưu & Tạo phiếu nhập kho\"",
                            "expected": "1. Direct Result: System returns 201 Created with new Goods Receipt containing 10 lines from Excel.\n2. Cross-functional Impact:\n   - Goods Receipts list displays new batch receipt.\n   - Batch detail drawer preview ready for QC and Post.",
                            "pre": "Tất cả mã sản phẩm/nguyên liệu trong file Excel đã tồn tại trong Catalog."
                        },
                        {
                            "id": "ITC_RCP_009",
                            "desc": "Import file Excel có dòng chứa mã mặt hàng không tồn tại",
                            "proc": "1. Upload Excel file with Line 3 Item Code=\"UNKNOWN-ITEM-999\"\n2. Click \"Phân tích file\"",
                            "expected": "1. Direct Result: System returns 400 Bad Request with error list: \"Dòng 3: Mã mặt hàng UNKNOWN-ITEM-999 không tồn tại\".\n2. Cross-functional Impact:\n   - UI highlights row 3 in red with error tooltip.\n   - No receipt created in DB; user must fix Excel file and re-upload.",
                            "pre": "File Excel có lỗi dữ liệu tham chiếu."
                        }
                    ]
                }
            ]
        },
        {
            "sheet_name": "Stock_Management",
            "feature_title": "Quản lý Tồn kho & Lịch sử biến động (Warehouse Stock & Transaction Audit Management)",
            "test_req": "Kiểm thử tích hợp tra cứu và kiểm toán tồn kho thực tế: hiển thị tổng hợp số dư tồn kho (on_hand, reserved, available) theo kho tổng và cửa hàng (/inventory/warehouse), xem chi tiết phân rã từng lô hàng kèm hạn sử dụng (FEFO Batch Detail Drawer), kiểm toán sổ nhật ký biến động giao dịch kho (/inventory/transactions) với đầy đủ các loại biến động (INBOUND, REPLENISHMENT, FULFILLMENT, ADJUSTMENT) và truy vết theo mã chứng từ.",
            "functions": [
                {
                    "name": "Function 1: Tra cứu tổng hợp tồn kho tại Kho tổng (Warehouse Inventory Summary)",
                    "cases": [
                        {
                            "id": "ITC_STK_001",
                            "desc": "Xem danh sách tổng hợp tồn kho sản phẩm và nguyên liệu kèm tìm kiếm từ khóa",
                            "proc": "1. Go to Warehouse Inventory page (/inventory/warehouse)\n2. Select Branch: \"Chi nhánh Hòa Lạc\"\n3. Enter Search Keyword: \"Ô Long\"\n4. Observe table results and pagination controls",
                            "expected": "1. Direct Result: System returns 200 OK with Page<WarehouseInventorySummaryResponse>:\n   - Columns displayed: Mã mặt hàng, Tên sản phẩm, Đơn vị tính, Tồn khả dụng (Available), Tồn tạm giữ (Reserved), Tổng thực tế (On-hand).\n2. Cross-functional Impact:\n   - Instant match against database inventory_balances joined with inventory_items.\n   - Pagination controls correctly report total elements and pages.",
                            "pre": "Database đã có dữ liệu tồn kho cho chi nhánh Hòa Lạc."
                        },
                        {
                            "id": "ITC_STK_002",
                            "desc": "Lọc tồn kho theo loại mặt hàng (Product vs Ingredient) và cảnh báo sắp hết",
                            "proc": "1. On Warehouse Inventory page\n2. Select Item Type Filter: \"INGREDIENT\"\n3. Verify only raw materials (Trà Búp, Hộp Thiếc, Túi Lọc) are returned\n4. Observe low stock warning badge for items below reorder point",
                            "expected": "1. Direct Result: System returns filtered list containing only itemType=\"INGREDIENT\". Items with on_hand <= reorderPoint flagged with warning icon.\n2. Cross-functional Impact:\n   - Purchasing staff can quickly identify which raw materials require purchase orders.",
                            "pre": "Có ít nhất 1 nguyên liệu có tồn kho dưới reorderPoint."
                        }
                    ]
                },
                {
                    "name": "Function 2: Xem chi tiết phân rã Lô hàng & Hạn sử dụng (Batch Detail Breakdown)",
                    "cases": [
                        {
                            "id": "ITC_STK_003",
                            "desc": "Mở Batch Detail Drawer xem danh sách các lô hàng kèm HSD theo nguyên tắc FEFO",
                            "proc": "1. On Warehouse Inventory page\n2. Locate item \"Trà Ô Long Thuần 200g\" (TOLT-200G)\n3. Click on the row or \"Chi tiết lô hàng\" button\n4. Drawer slides in from right displaying batch breakdown",
                            "expected": "1. Direct Result: System calls GET /api/inventory/warehouse/products/{itemId}/locations and returns batch details:\n   - Batch Number (Số lô), Production Date (NSX), Expiry Date (HSD), Quantity on hand.\n   - Batches ordered chronologically by expiry date ascending (FEFO).\n2. Cross-functional Impact:\n   - Warehouse staff can pick goods from nearest expiry batch.",
                            "pre": "Mặt hàng TOLT-200G có ít nhất 2 lô hàng khác hạn sử dụng."
                        }
                    ]
                },
                {
                    "name": "Function 3: Kiểm toán Nhật ký Biến động Tồn kho (Inventory Transaction Audit Log)",
                    "cases": [
                        {
                            "id": "ITC_STK_004",
                            "desc": "Tra cứu danh sách lịch sử biến động giao dịch kho với đầy đủ thông tin kiểm toán",
                            "proc": "1. Go to Inventory Transactions page (/inventory/transactions)\n2. Select Warehouse: \"Kho Tổng Hương Vân Trà\"\n3. Observe table rows and columns: Transaction Code, Transaction Type, Item Code, Batch, Quantity Change, Before/After Balance, Reference Code, Created Date, Operator",
                            "expected": "1. Direct Result: System returns 200 OK with PageResponse<InventoryTransactionResponse> containing complete audit trail.\n2. Cross-functional Impact:\n   - Every stock in/out/transfer strictly traceable to source invoice or slip code.",
                            "pre": "Hệ thống đã phát sinh ít nhất 5 giao dịch nhập/xuất kho."
                        },
                        {
                            "id": "ITC_STK_005",
                            "desc": "Lọc lịch sử biến động theo loại giao dịch và khoảng thời gian",
                            "proc": "1. On Inventory Transactions page\n2. Select Transaction Type: \"INBOUND_RECEIPT\"\n3. Select Date Range: From 2026-11-01 To 2026-11-07\n4. Click \"Tìm kiếm\" button",
                            "expected": "1. Direct Result: System returns transactions matching transactionType=\"INBOUND_RECEIPT\" within specified date range.\n2. Cross-functional Impact:\n   - Facilitates monthly warehouse audit reconciliation.",
                            "pre": "Có giao dịch nhập kho trong tháng 11/2026."
                        },
                        {
                            "id": "ITC_STK_006",
                            "desc": "Tra cứu chi tiết một giao dịch kho theo ID",
                            "proc": "(Using api to simulate in testing)\n1. Send HTTP GET to /api/inventory/inventory-transactions/{transactionId}\n2. Verify response payload and reference integrity",
                            "expected": "1. Direct Result: System returns 200 OK with InventoryTransactionResponse detailing previousBalance, newBalance, diff, and actor username.\n2. Cross-functional Impact:\n   - Audit log immutable; ensures regulatory compliance.",
                            "pre": "Giao dịch transactionId tồn tại trong DB."
                        }
                    ]
                }
            ]
        },
        {
            "sheet_name": "Replenishment_Management",
            "feature_title": "Quản lý Chuyển hàng lên kệ POS (Shelf Replenishment Management)",
            "test_req": "Kiểm thử tích hợp toàn diện chu trình chuyển hàng từ kho tổng ra kệ trưng bày POS: tạo phiếu yêu cầu chuyển kệ (Draft), tự động kiểm tra tồn kho và tạm giữ hàng (Reserved Balance) tại kho tổng, bắt đầu xuất kho chuyển (In-Progress), nhân viên hoàn tất xếp lên kệ (Complete) làm tăng tồn trên kệ StoreShelf và trừ dứt điểm tồn kho tổng; kiểm tra hủy phiếu (Cancel) hoàn trả số lượng tạm giữ về Available, và kiểm tra chặn chuyển quá sức chứa kệ (Capacity Overflow).",
            "functions": [
                {
                    "name": "Function 1: Lập phiếu chuyển hàng lên kệ POS (Create Shelf Replenishment)",
                    "cases": [
                        {
                            "id": "ITC_REP_001",
                            "desc": "Tạo phiếu chuyển hàng từ kho tổng lên kệ POS hợp lệ $\\rightarrow$ Tự động tạm giữ hàng (Reserve)",
                            "proc": "1. Go to Shelf Replenishments page (/inventory/shelf-replenishments)\n2. Click \"Tạo phiếu chuyển kệ\" (/inventory/shelf-replenishments/new)\n3. Select Source Warehouse: \"Kho Tổng Hương Vân Trà\"\n4. Select Target Shelf: \"Kệ Trà Ô Long A01\" (SHELF-HL-A01)\n5. Add Line: Select Item \"Trà Ô Long Thuần 200g\" (TOLT-200G), Batch=\"BATCH-2026-001\", Qty=30 (hộp)\n6. Click \"Lưu & Xác nhận chuyển\"",
                            "expected": "1. Direct Result: System returns 201 Created and persists ShelfReplenishmentResponse:\n   - id=\"990e8400-e29b-41d4-a716-446655440001\" (Regex: ^[0-9a-fA-F-]{36}$)\n   - slipCode=\"REP-202611-00001\" (Regex: ^REP-\\d{6}-\\d{5}$), status=\"DRAFT\" / \"IN_PROGRESS\"\n   - Database: In warehouse inventory_balances, quantity_reserved increments by +30, quantity_available decreases by -30.\n2. Cross-functional Impact:\n   - Shelf Replenishments table displays new transfer slip.\n   - Warehouse inventory view immediately reflects reserved goods, preventing concurrent overselling.",
                            "pre": "Kho tổng có ít nhất 50 hộp TOLT-200G available; kệ A01 có sức chứa trống >= 30."
                        },
                        {
                            "id": "ITC_REP_002",
                            "desc": "Tạo phiếu chuyển kệ với số lượng vượt quá tồn khả dụng tại kho tổng",
                            "proc": "1. Go to Shelf Replenishment creation form\n2. Select Item with available stock=10\n3. Enter Transfer Quantity=50\n4. Click \"Lưu & Xác nhận chuyển\"",
                            "expected": "1. Direct Result: System returns 400 Bad Request: \"Số lượng chuyển vượt quá tồn kho khả dụng tại kho nguồn\".\n2. Cross-functional Impact:\n   - Form displays validation toast error.\n   - No reservation created; database rollback keeps balances intact.",
                            "pre": "Tồn kho khả dụng của sản phẩm tại kho nguồn nhỏ hơn 50."
                        },
                        {
                            "id": "ITC_REP_003",
                            "desc": "Tạo phiếu chuyển kệ với số lượng vượt quá sức chứa tối đa của kệ",
                            "proc": "1. Go to Shelf Replenishment creation form\n2. Select Shelf with remaining capacity=20\n3. Enter Transfer Quantity=50\n4. Click \"Lưu & Xác nhận chuyển\"",
                            "expected": "1. Direct Result: System returns 400 Bad Request: \"Số lượng chuyển vượt quá sức chứa còn lại của kệ hàng (còn trống 20)\".\n2. Cross-functional Impact:\n   - Prevents physical shelf overloading at POS shop.\n   - Transaction aborted without DB changes.",
                            "pre": "Kệ hàng chỉ còn sức chứa trống 20 hộp."
                        }
                    ]
                },
                {
                    "name": "Function 2: Thực thi xuất kho & Hoàn tất lên kệ (In-Progress & Complete Replenishment)",
                    "cases": [
                        {
                            "id": "ITC_REP_004",
                            "desc": "Chuyển trạng thái phiếu sang IN_PROGRESS khi nhân viên kho bắt đầu bốc xếp",
                            "proc": "1. Open replenishment slip \"REP-202611-00001\"\n2. Click \"Bắt đầu vận chuyển\" button\n3. Confirm dialog: \"Bắt đầu chuyển hàng ra quầy POS?\"\n4. Click \"Xác nhận\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"IN_PROGRESS\".\n2. Cross-functional Impact:\n   - Replenishment slip badge changes to orange \"Đang chuyển\".\n   - POS store shelf staff notified of incoming delivery.",
                            "pre": "Phiếu chuyển đang ở trạng thái DRAFT."
                        },
                        {
                            "id": "ITC_REP_005",
                            "desc": "Xác nhận hoàn tất xếp hàng lên kệ $\\rightarrow$ Trừ tồn kho tổng & cộng tồn kệ POS",
                            "proc": "1. Store staff opens slip \"REP-202611-00001\"\n2. Verify goods received physically at shelf\n3. Click \"Hoàn tất lên kệ\" button\n4. Confirm dialog: \"Xác nhận đã xếp đủ 30 hộp lên kệ SHELF-HL-A01?\"\n5. Click \"Xác nhận\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"COMPLETED\":\n   - Database: Warehouse inventory_balances decrements quantity_reserved (-30) and quantity_on_hand (-30).\n   - Database: Store shelf currentStock increments (+30).\n   - Database: inventory_transactions records 2 transactions: REPLENISHMENT_OUT and REPLENISHMENT_IN.\n2. Cross-functional Impact:\n   - POS cashier terminal instantly sees +30 units available for retail scanning.\n   - Warehouse inventory view shows completed deduction.",
                            "pre": "Phiếu chuyển đang ở trạng thái IN_PROGRESS."
                        }
                    ]
                },
                {
                    "name": "Function 3: Hủy phiếu chuyển kệ & Hoàn trả tồn (Cancel & Revert Replenishment)",
                    "cases": [
                        {
                            "id": "ITC_REP_006",
                            "desc": "Hủy phiếu chuyển kệ khi đang Draft/In-Progress $\\rightarrow$ Tự động hoàn trả số lượng Reserved",
                            "proc": "1. Open slip in status DRAFT or IN_PROGRESS with reserved Qty=30\n2. Click \"Hủy phiếu\" button\n3. Enter Cancel Reason: \"Kệ đang bảo trì sửa chữa\"\n4. Click \"Xác nhận hủy\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"CANCELLED\":\n   - Database: Warehouse inventory_balances decrements quantity_reserved (-30) and increments quantity_available (+30).\n2. Cross-functional Impact:\n   - Warehouse available stock instantly restored to normal.\n   - Prevents frozen/locked stock leak.",
                            "pre": "Phiếu chuyển đang ở trạng thái DRAFT hoặc IN_PROGRESS có reserved stock."
                        }
                    ]
                },
                {
                    "name": "Function 4: Tổng hợp & Thống kê Phiếu Chuyển Kệ (Replenishment Summary)",
                    "cases": [
                        {
                            "id": "ITC_REP_007",
                            "desc": "Xem báo cáo tóm tắt số lượng phiếu chuyển kệ theo các trạng thái",
                            "proc": "1. Go to Shelf Replenishments page\n2. Observe top KPI cards: Tổng phiếu, Đang chờ chuyển, Đang vận chuyển, Đã hoàn tất",
                            "expected": "1. Direct Result: System calls GET /api/inventory/shelf-replenishments/summary and returns accurate counts matching database status tallies.\n2. Cross-functional Impact:\n   - Store manager has real-time overview of shelf replenishment efficiency.",
                            "pre": "Có các phiếu chuyển kệ ở nhiều trạng thái khác nhau."
                        }
                    ]
                }
            ]
        },
        {
            "sheet_name": "Production_Management",
            "feature_title": "Quản lý Lệnh sản xuất đóng gói (Production Order Management)",
            "test_req": "Kiểm thử tích hợp chu trình sản xuất và đóng gói trà thành phẩm theo công thức định mức BOM: lập lệnh sản xuất (Draft) với số lượng kế hoạch, tự động bóc tách định mức nguyên liệu cần dùng từ Catalog, duyệt lệnh và tạm giữ nguyên liệu (Submit & Approve), hoàn tất sản xuất (Complete) tự động xuất trừ tồn kho nguyên liệu thô (Ingredient Stock-Out), nhập kho lô trà thành phẩm mới (Finished Product Stock-In), và kiểm tra hủy lệnh (Cancel) giải phóng nguyên liệu.",
            "functions": [
                {
                    "name": "Function 1: Lập lệnh sản xuất đóng gói mới (Create Production Order)",
                    "cases": [
                        {
                            "id": "ITC_PRD_001",
                            "desc": "Lập lệnh sản xuất trà hộp thành phẩm từ BOM với số lượng kế hoạch hợp lệ",
                            "proc": "1. Go to Production Orders page (/inventory/production-orders)\n2. Click \"Tạo lệnh sản xuất\" (/inventory/production-orders/new)\n3. Select Finished Product: \"Trà Ô Long Thuần 200g\" (TOLT-200G)\n4. Enter Plan Quantity: 500 (hộp)\n5. Select Production Warehouse: \"Kho Tổng Hương Vân Trà\"\n6. Enter Expected Completion Date: \"2026-11-15\"\n7. Notes: \"Đóng gói lô trà xuất khẩu đợt 1\"\n8. Click \"Lưu lệnh sản xuất\"",
                            "expected": "1. Direct Result: System returns 201 Created and persists ProductionOrderResponse:\n   - id=\"aa0e8400-e29b-41d4-a716-446655440001\" (Regex: ^[0-9a-fA-F-]{36}$)\n   - orderCode=\"PO-202611-00001\" (Regex: ^PO-\\d{6}-\\d{5}$), status=\"DRAFT\", planQuantity=500\n   - System queries Catalog BOM: auto-generates production_order_lines with required ingredient quantities (100kg Trà búp + 500 Hộp thiếc).\n2. Cross-functional Impact:\n   - Production Orders table displays new order with status DRAFT.\n   - Ingredients are calculated but NOT yet deducted from inventory.",
                            "pre": "Sản phẩm TOLT-200G đã được khai báo BOM đầy đủ bên Catalog-Service."
                        },
                        {
                            "id": "ITC_PRD_002",
                            "desc": "Lập lệnh sản xuất cho sản phẩm không có định mức BOM",
                            "proc": "1. Go to Production Orders page\n2. Click \"Tạo lệnh sản xuất\"\n3. Select Product without BOM formula (Variant without ingredients)\n4. Enter Plan Quantity=100\n5. Click \"Lưu lệnh sản xuất\"",
                            "expected": "1. Direct Result: System returns 400 Bad Request: \"Sản phẩm này chưa được định nghĩa công thức BOM (định mức nguyên liệu)\".\n2. Cross-functional Impact:\n   - Toast error displayed; stops creation of unproducible order.",
                            "pre": "Sản phẩm chọn chưa có BOM bên Catalog."
                        }
                    ]
                },
                {
                    "name": "Function 2: Duyệt & Tạm giữ nguyên liệu (Submit & Approve Production Order)",
                    "cases": [
                        {
                            "id": "ITC_PRD_003",
                            "desc": "Quản lý duyệt lệnh sản xuất $\\rightarrow$ Hệ thống tự động khóa giữ (Reserve) nguyên liệu thô",
                            "proc": "1. Open production order \"PO-202611-00001\"\n2. Click \"Gửi duyệt\" $\\rightarrow$ Status becomes SUBMITTED\n3. Manager clicks \"Phê duyệt sản xuất\" (Approve)\n4. System checks raw material availability against BOM",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"APPROVED\":\n   - Database: In inventory_balances, reserves 100kg Trà búp and 500 Hộp thiếc (quantity_reserved increased).\n2. Cross-functional Impact:\n   - Raw materials are locked, preventing other production orders or sales from seizing them.\n   - Production order badge turns blue \"Đã phê duyệt\".",
                            "pre": "Kho tổng có đủ tồn nguyên liệu Trà búp >= 100kg và Hộp thiếc >= 500."
                        },
                        {
                            "id": "ITC_PRD_004",
                            "desc": "Phê duyệt lệnh sản xuất khi kho không đủ tồn nguyên liệu theo BOM",
                            "proc": "1. Submit production order requiring 1000kg Trà búp while warehouse available stock=200kg\n2. Manager clicks \"Phê duyệt sản xuất\"",
                            "expected": "1. Direct Result: System returns 400 Bad Request: \"Không đủ tồn kho nguyên vật liệu: Trà búp thiếu 800kg để sản xuất\".\n2. Cross-functional Impact:\n   - Order status remains SUBMITTED.\n   - UI alerts shortage details, prompting purchase order for raw material.",
                            "pre": "Tồn kho nguyên liệu khả dụng nhỏ hơn định mức BOM yêu cầu."
                        }
                    ]
                },
                {
                    "name": "Function 3: Hoàn tất sản xuất & Nhập kho thành phẩm (Complete Production Order)",
                    "cases": [
                        {
                            "id": "ITC_PRD_005",
                            "desc": "Hoàn tất sản xuất $\\rightarrow$ Trừ dứt điểm nguyên liệu thô & Nhập kho lô thành phẩm trà",
                            "proc": "1. Production team completes packaging 500 boxes\n2. Open approved production order \"PO-202611-00001\"\n3. Click \"Hoàn tất sản xuất\" button\n4. Enter Actual Produced Qty: 500, Manufacturing Date=\"2026-11-07\", Expiry Date=\"2028-11-07\"\n5. Click \"Xác nhận hoàn tất\"",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"COMPLETED\":\n   - Raw Materials: inventory_balances decrements reserved and on_hand by -100kg Trà búp, -500 Hộp thiếc.\n   - Finished Goods: Automatically generates new InventoryBatch for finished tea (TOLT-200G) and increments on_hand by +500.\n   - Generates PRODUCTION_OUT and PRODUCTION_IN audit transactions.\n2. Cross-functional Impact:\n   - Warehouse Inventory page instantly shows +500 finished tea boxes ready for sale.\n   - Raw materials inventory decremented cleanly.",
                            "pre": "Lệnh sản xuất ở trạng thái APPROVED."
                        }
                    ]
                },
                {
                    "name": "Function 4: Hủy lệnh sản xuất & Giải phóng nguyên liệu (Cancel Production Order)",
                    "cases": [
                        {
                            "id": "ITC_PRD_006",
                            "desc": "Hủy lệnh sản xuất $\\rightarrow$ Giải phóng hoàn toàn số lượng nguyên liệu đã tạm giữ",
                            "proc": "1. Open approved order \"PO-202611-00001\" (currently holding 100kg reserved tea)\n2. Click \"Hủy lệnh sản xuất\"\n3. Enter cancel reason: \"Khách hàng hủy hợp đồng đặt hàng\"\n4. Confirm cancellation",
                            "expected": "1. Direct Result: System returns 200 OK with status=\"CANCELLED\":\n   - Database: Releases 100kg reserved tea back to available inventory (reserved decreased, available increased).\n2. Cross-functional Impact:\n   - Raw material stock unlocked immediately for other orders.",
                            "pre": "Lệnh sản xuất đang ở trạng thái APPROVED."
                        }
                    ]
                },
                {
                    "name": "Function 5: Cảnh báo nhu cầu sản xuất (Production Required Alerts)",
                    "cases": [
                        {
                            "id": "ITC_PRD_007",
                            "desc": "Xem danh sách cảnh báo các mặt hàng trà thành phẩm chạm ngưỡng cần sản xuất",
                            "proc": "1. Go to Production Orders page or Dashboard\n2. Observe \"Cảnh báo cần sản xuất\" banner / table\n3. System calls GET /api/inventory/alerts/production-required",
                            "expected": "1. Direct Result: System returns List<ProductionRequiredAlertResponse> containing products with available stock <= productionThreshold.\n2. Cross-functional Impact:\n   - 1-click button \"Tạo lệnh sản xuất nhanh\" auto-fills the product and recommended quantity.",
                            "pre": "Có sản phẩm trà thành phẩm có tồn kho khả dụng <= productionThreshold."
                        }
                    ]
                }
            ]
        }
    ]

    # Process each feature sheet
    for feat in features_data:
        sheet_name = feat["sheet_name"]
        if sheet_name in wb.sheetnames:
            del wb[sheet_name]
        ws = wb.create_sheet(title=sheet_name)

        # Set column dimensions
        for col_letter, width in col_widths.items():
            ws.column_dimensions[col_letter].width = width

        # Row 1 empty
        # Row 2: Feature title
        ws.cell(2, 1, "Feature").font = font_bold
        ws.cell(2, 2, feat["feature_title"]).font = font_bold

        # Row 3: Test requirement
        ws.cell(3, 1, "Test requirement").font = font_bold
        ws.cell(3, 2, feat["test_req"]).font = font_regular
        ws.cell(3, 2).alignment = align_left

        # Row 4: Number of TCs
        ws.cell(4, 1, "Number of TCs").font = font_bold
        ws.cell(4, 2, "=B6+C6+D6+E6").font = font_bold

        # Row 5: Testing Round Header
        headers_r5 = ["Testing Round", "Passed", "Failed", "Pending", "N/A"]
        for c_idx, val in enumerate(headers_r5, start=1):
            cell = ws.cell(5, c_idx, val)
            cell.font = font_bold
            cell.alignment = align_center

        # Rows 6-8: Testing rounds count formulas
        ws.cell(6, 1, "Round 1").font = font_bold
        ws.cell(6, 2, "=COUNTIF($F11:$F998,B5)").font = font_regular
        ws.cell(6, 3, "=COUNTIF($F11:$F998,C5)").font = font_regular
        ws.cell(6, 4, "=COUNTIF($F11:$F998,D5)").font = font_regular
        ws.cell(6, 5, "=COUNTIF($F11:$F998,E5)").font = font_regular

        ws.cell(7, 1, "Round 2").font = font_bold
        ws.cell(7, 2, "=COUNTIF($I11:$I998,B5)").font = font_regular
        ws.cell(7, 3, "=COUNTIF($I11:$I998,C5)").font = font_regular
        ws.cell(7, 4, "=COUNTIF($I11:$I998,D5)").font = font_regular
        ws.cell(7, 5, "=COUNTIF($I11:$I998,E5)").font = font_regular

        ws.cell(8, 1, "Round 3").font = font_bold
        ws.cell(8, 2, "=COUNTIF($L11:$L998,B5)").font = font_regular
        ws.cell(8, 3, "=COUNTIF($L11:$L998,C5)").font = font_regular
        ws.cell(8, 4, "=COUNTIF($L11:$L998,D5)").font = font_regular
        ws.cell(8, 5, "=COUNTIF($L11:$L998,E5)").font = font_regular

        for r in range(5, 9):
            for c in range(1, 6):
                ws.cell(r, c).border = thin_border

        # Row 10: Table Header
        headers_r10 = [
            "Test Case ID", "Test Case Description", "Test Case Procedure",
            "Expected Results", "Pre-conditions",
            "Round 1", "Test date", "Tester",
            "Round 2", "Test date", "Tester",
            "Round 3", "Test date", "Tester"
        ]
        ws.row_dimensions[10].height = 28
        for c_idx, val in enumerate(headers_r10, start=1):
            cell = ws.cell(10, c_idx, val)
            cell.font = font_header
            cell.fill = fill_header
            cell.alignment = align_center
            cell.border = thin_border

        current_row = 11
        for func in feat["functions"]:
            # Function header row
            ws.row_dimensions[current_row].height = 22
            func_cell = ws.cell(current_row, 1, func["name"])
            func_cell.font = font_bold
            func_cell.fill = fill_func
            func_cell.alignment = Alignment(horizontal="left", vertical="center")
            func_cell.border = thin_border
            for c_idx in range(2, 15):
                empty_c = ws.cell(current_row, c_idx)
                empty_c.fill = fill_func
                empty_c.border = thin_border
            current_row += 1

            for tc in func["cases"]:
                ws.row_dimensions[current_row].height = 110

                c1 = ws.cell(current_row, 1, tc["id"])
                c1.font = font_bold
                c1.alignment = align_center_top
                c1.border = thin_border

                c2 = ws.cell(current_row, 2, tc["desc"])
                c2.font = font_regular
                c2.alignment = align_left
                c2.border = thin_border

                c3 = ws.cell(current_row, 3, tc["proc"])
                c3.font = font_regular
                c3.alignment = align_left
                c3.border = thin_border

                c4 = ws.cell(current_row, 4, tc["expected"])
                c4.font = font_regular
                c4.alignment = align_left
                c4.border = thin_border

                c5 = ws.cell(current_row, 5, tc["pre"])
                c5.font = font_regular
                c5.alignment = align_left
                c5.border = thin_border

                # Round 1
                c6 = ws.cell(current_row, 6, "Passed")
                c6.font = font_passed
                c6.fill = fill_passed
                c6.alignment = align_center_top
                c6.border = thin_border

                c7 = ws.cell(current_row, 7, "07/11/2026")
                c7.font = font_regular
                c7.alignment = align_center_top
                c7.border = thin_border

                c8 = ws.cell(current_row, 8, "AnNV")
                c8.font = font_regular
                c8.alignment = align_center_top
                c8.border = thin_border

                # Round 2
                c9 = ws.cell(current_row, 9, "Pending")
                c9.font = font_pending
                c9.fill = fill_pending
                c9.alignment = align_center_top
                c9.border = thin_border

                c10 = ws.cell(current_row, 10, None)
                c10.border = thin_border

                c11 = ws.cell(current_row, 11, None)
                c11.border = thin_border

                # Round 3
                c12 = ws.cell(current_row, 12, "Pending")
                c12.font = font_pending
                c12.fill = fill_pending
                c12.alignment = align_center_top
                c12.border = thin_border

                c13 = ws.cell(current_row, 13, None)
                c13.border = thin_border

                c14 = ws.cell(current_row, 14, None)
                c14.border = thin_border

                current_row += 1

    # Update Test Cases sheet
    ws_tc = wb["Test Cases"]
    # Find last row in Test Cases
    last_tc_row = 8
    for r in range(9, 200):
        if ws_tc.cell(r, 2).value is not None:
            last_tc_row = r

    # Count current functions
    current_func_num = 38
    new_tc_row = last_tc_row + 1

    for feat in features_data:
        sheet_name = feat["sheet_name"]
        for func in feat["functions"]:
            current_func_num += 1
            ws_tc.cell(new_tc_row, 2, f"{current_func_num}.0").font = font_regular
            ws_tc.cell(new_tc_row, 2).alignment = align_center
            ws_tc.cell(new_tc_row, 2).border = thin_border

            ws_tc.cell(new_tc_row, 3, func["name"]).font = font_regular
            ws_tc.cell(new_tc_row, 3).alignment = align_left
            ws_tc.cell(new_tc_row, 3).border = thin_border

            ws_tc.cell(new_tc_row, 4, sheet_name).font = font_regular
            ws_tc.cell(new_tc_row, 4).alignment = align_left
            ws_tc.cell(new_tc_row, 4).border = thin_border

            # Short description from first case
            first_case = func["cases"][0]
            ws_tc.cell(new_tc_row, 5, first_case["desc"]).font = font_regular
            ws_tc.cell(new_tc_row, 5).alignment = align_left
            ws_tc.cell(new_tc_row, 5).border = thin_border

            ws_tc.cell(new_tc_row, 6, first_case["pre"]).font = font_regular
            ws_tc.cell(new_tc_row, 6).alignment = align_left
            ws_tc.cell(new_tc_row, 6).border = thin_border

            new_tc_row += 1

    # Update Test Statistics sheet
    ws_stat = wb["Test Statistics"]
    # Rows 11-17: Catalog
    # Rows 18-22: 5 inventory modules
    new_modules = [
        ("8.0", "Location_Management"),
        ("9.0", "GoodsReceipt_Management"),
        ("10.0", "Stock_Management"),
        ("11.0", "Replenishment_Management"),
        ("12.0", "Production_Management")
    ]

    for idx, (no, m_sheet) in enumerate(new_modules, start=18):
        ws_stat.cell(idx, 2, no).font = font_regular
        ws_stat.cell(idx, 2).alignment = align_center
        ws_stat.cell(idx, 2).border = thin_border

        ws_stat.cell(idx, 3, f"='{m_sheet}'!B2").font = font_regular
        ws_stat.cell(idx, 3).border = thin_border

        ws_stat.cell(idx, 4, f"='{m_sheet}'!B6").font = font_regular
        ws_stat.cell(idx, 4).alignment = align_center
        ws_stat.cell(idx, 4).border = thin_border

        ws_stat.cell(idx, 5, f"='{m_sheet}'!C6").font = font_regular
        ws_stat.cell(idx, 5).alignment = align_center
        ws_stat.cell(idx, 5).border = thin_border

        ws_stat.cell(idx, 6, f"='{m_sheet}'!D6").font = font_regular
        ws_stat.cell(idx, 6).alignment = align_center
        ws_stat.cell(idx, 6).border = thin_border

        ws_stat.cell(idx, 7, f"='{m_sheet}'!E6").font = font_regular
        ws_stat.cell(idx, 7).alignment = align_center
        ws_stat.cell(idx, 7).border = thin_border

        ws_stat.cell(idx, 8, f"='{m_sheet}'!B4").font = font_regular
        ws_stat.cell(idx, 8).alignment = align_center
        ws_stat.cell(idx, 8).border = thin_border

    # Sub total row moves to 24 (row 23 empty)
    ws_stat.cell(23, 2, None)
    ws_stat.cell(24, 3, "Sub total").font = font_bold
    ws_stat.cell(24, 3).alignment = Alignment(horizontal="right", vertical="center")
    ws_stat.cell(24, 4, "=SUM(D11:D22)").font = font_bold
    ws_stat.cell(24, 4).alignment = align_center
    ws_stat.cell(24, 5, "=SUM(E11:E22)").font = font_bold
    ws_stat.cell(24, 5).alignment = align_center
    ws_stat.cell(24, 6, "=SUM(F11:F22)").font = font_bold
    ws_stat.cell(24, 6).alignment = align_center
    ws_stat.cell(24, 7, "=SUM(G11:G22)").font = font_bold
    ws_stat.cell(24, 7).alignment = align_center
    ws_stat.cell(24, 8, "=SUM(H11:H22)").font = font_bold
    ws_stat.cell(24, 8).alignment = align_center

    for c in range(2, 9):
        ws_stat.cell(24, c).border = thin_border

    # Coverage formulas in rows 26-27
    ws_stat.cell(26, 3, "Test coverage").font = font_bold
    ws_stat.cell(26, 5, "=(D24+E24)*100/(H24-G24)").font = font_bold
    ws_stat.cell(26, 6, "%").font = font_bold

    ws_stat.cell(27, 3, "Test successful coverage").font = font_bold
    ws_stat.cell(27, 5, "=D24*100/(H24-G24)").font = font_bold
    ws_stat.cell(27, 6, "%").font = font_bold

    # Clear old formulas in rows 19-22 of Test Statistics if needed
    # Note: Row 19, 21, 22 previously had sub total. Now replaced by rows 18-22 modules and row 24 sub total.

    # Update Notes in row 6 of Test Statistics
    ws_stat.cell(6, 3, "Release 1 includes 7 Catalog modules and 5 Inventory modules: Location_Management, GoodsReceipt_Management, Stock_Management, Replenishment_Management, Production_Management.")

    wb.save(file_path)
    print("Report successfully updated with 5 Inventory features!")

if __name__ == "__main__":
    build_inventory_it_report()

