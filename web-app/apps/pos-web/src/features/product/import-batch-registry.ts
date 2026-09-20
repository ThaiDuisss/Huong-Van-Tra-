import { ProductImportItemsList } from "./components/ProductImportItemsList";
import { ProductVariantImportItemsList } from "./components/ProductVariantImportItemsList";

/** Đăng ký component hiển thị danh sách dòng theo từng loại import — thêm loại mới chỉ cần thêm 1 dòng ở đây. */
export const IMPORT_ITEMS_LIST_REGISTRY = {
    PRODUCT: ProductImportItemsList,
    PRODUCT_VARIANT: ProductVariantImportItemsList
};
