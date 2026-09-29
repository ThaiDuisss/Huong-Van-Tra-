# SC-03 - Create Supplier Receipt Documentation

## HVT Goods Receipt Management System

This folder contains comprehensive documentation for the SC-03 workflow: "Create Supplier Receipt" as implemented in the Huong Van Tea (HVT) Point of Sale and Inventory Management System.

---

## 📁 Files Included

### 1. **SC-03-Create-Supplier-Receipt.docx**
- **Format:** Microsoft Word (.docx)
- **Content:** Complete functional requirement specification including:
  - Business Flow description
  - Primary Actors and their responsibilities
  - Pre-conditions and Post-conditions
  - Status Workflow diagram
  - Related Features
  - Normal Flow (18-step process)
  - Key Exceptions (10 exception scenarios)
  - Data & System Interactions (Database tables, API endpoints)

### 2. **SC-03-Create-Supplier-Receipt.drawio**
- **Format:** Draw.io XML format
- **Usage:** Open with [draw.io](https://app.diagrams.net/) to view and edit the activity diagram
- **Content:** Interactive activity diagram showing the complete workflow
- **Instructions:**
  1. Go to https://app.diagrams.net/
  2. Click "File" → "Open" → "Browse" and select this file
  3. View the diagram layout
  4. Export to other formats (PNG, PDF, SVG) if needed

### 3. **SC-03-Create-Supplier-Receipt-Activity-Diagram.png**
- **Format:** PNG image (300 DPI, high quality)
- **Size:** 4170 x 5850 pixels
- **Content:** Visual representation of the activity diagram showing:
  - Start/End nodes
  - Process activities
  - Decision points (Data validation, Manager approval, QC validation)
  - Flow connections with decision labels (Yes/No)
  - Complete receipt lifecycle from creation to posting

### 4. **SC-03-Activity-Diagram.dot**
- **Format:** Graphviz DOT format
- **Usage:** Can be converted to other image formats using Graphviz
- **Reference:** For developers who prefer text-based diagram definitions

---

## 🔄 SC-03 Workflow Overview

The workflow follows these main stages:

```
[DRAFT] → [PENDING_APPROVAL] → [APPROVED] → [RECEIVING] → [QC_PENDING] → [QC_COMPLETED] → [POSTED]
```

### Key Actors
- **Warehouse Staff (INVENTORY_STAFF):** Creates receipt, enters item lines, confirms physical receipt
- **Inventory Manager / Admin / Branch Manager:** Approves receipt, performs QC, posts final receipt

### Core Process Steps
1. Create receipt header with supplier, document number, receiving location
2. Add item lines with material details and quantities
3. Submit for approval (Inventory Manager review)
4. Start physical goods receipt and verification
5. Perform quality control (QC) inspection
6. Enter batch information (Supplier Lot, Manufacture Date, Expiry Date)
7. Confirm QC results
8. Create inventory batches and update warehouse stock
9. Generate inbound slip and record ledger
10. Post receipt to finalize

---

## 💾 Implementation Reference

### Database Tables
- `goods_receipt` - Receipt header information
- `goods_receipt_line` - Line items
- `goods_receipt_batch` - Batch allocations created from QC
- `inventory_movement` - Stock ledger
- `stock_document` - Inbound slip
- `inventory_batch_allocation` - FEFO batch tracking

### API Endpoints
- `POST /api/goods-receipt` - Create receipt
- `POST /api/goods-receipt/{id}/submit` - Submit for approval
- `POST /api/goods-receipt/{id}/approve` - Approve receipt
- `POST /api/goods-receipt/{id}/start-receiving` - Start physical receipt
- `POST /api/goods-receipt/{id}/confirm-receiving` - Confirm physical count
- `POST /api/goods-receipt/{id}/qc` - Perform QC inspection
- `POST /api/goods-receipt/{id}/post` - Post/finalize receipt

### Code Files (HVT Project)
- **Frontend:** `/web-app/apps/pos-web/src/features/inventory/pages/GoodsReceiptDetailPage.tsx`
- **Components:** `/web-app/apps/pos-web/src/features/inventory/components/GoodsReceiptComponents.tsx`
- **Types:** `/web-app/apps/pos-web/src/features/inventory/types/goods-receipt.types.ts`
- **API Service:** `/web-app/apps/pos-web/src/features/inventory/services/goodsReceipt.api.ts`

---

## 📋 How to Use This Documentation

### For Business Analysts / Project Managers
- Read the **SC-03-Create-Supplier-Receipt.docx** for complete business requirements
- Review the **PNG diagram** for visual workflow understanding
- Reference the **Normal Flow** and **Key Exceptions** sections

### For Developers
- Review the **docx file** for API endpoint requirements
- Study the **activity diagram** (.drawio) to understand all decision points
- Reference the code files mentioned above
- Pay attention to validation rules and exception handling

### For QA/Testing
- Use the **Normal Flow** (18 steps) as a test scenario template
- Test all **Key Exceptions** (10 exception scenarios)
- Verify status transitions and permission checks
- Validate batch information requirements

---

## 🔍 How to Edit the Diagram

If you need to modify the activity diagram:

1. Open **SC-03-Create-Supplier-Receipt.drawio** in [draw.io](https://app.diagrams.net/)
2. Make your changes
3. Save and export to PNG: File → Export As → PNG
4. Update this documentation if workflow changes

---

## ✅ Validation Checklist

Before using this documentation:

- [ ] Review all 18 steps in Normal Flow
- [ ] Verify all 10 exception scenarios match your system
- [ ] Check database tables exist and have correct schema
- [ ] Verify API endpoints match your backend
- [ ] Test permission roles (INVENTORY_STAFF, INVENTORY_MANAGER, QC_STAFF, ADMIN, BRANCH_MANAGER)
- [ ] Validate batch information requirements (Supplier Lot, MFG Date, Expiry Date)
- [ ] Confirm FEFO (First Expire First Out) batch allocation logic

---

## 📞 Support

For questions or updates to this documentation:
- Refer to the main HVT project documentation
- Contact the development team
- Review the code implementation in the HVT repository

---

**Document Version:** 1.0  
**Date Created:** 2026-09-29  
**Based on:** SU26_SEP490_G66_Report_7_Final_Project.docx (Section III.3.1 - SC-03)  
**System:** Huong Van Tea Point of Sale and Inventory Management System (HVTPOSIMS)

