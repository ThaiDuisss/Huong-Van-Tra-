# POS design references

## Scope

Only implement the "Bán hàng tại quầy" flow.

Do not implement:

- Customer web
- Online sales
- Management dashboard
- Inventory management
- Supplier management
- Reports

## Screens

### 01-pos-overview.png

Main POS sales screen.

Contains:

- Left sidebar
- Product search
- Product/cart item list
- Customer selector
- Discount selector
- Payment summary
- Quick cash buttons
- Payment methods
- Complete payment button

This is the primary layout reference.

### 02-product-search.png

Product search and selection state.

Expected behavior:

- Search by product name
- Search by SKU
- Search by barcode
- Add selected product to the current cart

### 03-customer-search.png

Customer search modal.

Expected behavior:

- Search by name
- Search by phone number
- Select an existing customer
- Allow a sale without customer

### 04-discount-search.png

Discount search modal.

Expected behavior:

- Search by discount code
- Show applicable promotions
- Select one discount
- Remove selected discount

### 05-payment-bank-transfer.png

Bank transfer payment modal.

### 06-payment-e-wallet.png

E-wallet payment modal.

### 07-payment-card.png

Card payment modal.

### 08-payment-combined.png

Combined payment modal.

### 09-receipt.png

Receipt preview after successful payment.

## General rules

- Build real React components.
- Never render a full screenshot as the application UI.
- Screenshots are visual references only.
- Use Tailwind CSS.
- Use TypeScript strict mode.
- Do not use `any`.
- Prefer reusable components.
- Desktop-first POS layout.
- Primary target resolution: 1440x900.