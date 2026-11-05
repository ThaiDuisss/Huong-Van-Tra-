import { ChevronLeft, ChevronRight, Search } from "lucide-react";
import type { CSSProperties, InputHTMLAttributes, ReactNode } from "react";
import { getOrderPageNumbers, getOrderRecordRange } from "./order-list-ui.utils";
import styles from "./OrderListUI.module.css";

interface OrderPageHeaderProps {
  title: string;
  description: ReactNode;
  actions?: ReactNode;
}

export function OrderPageHeader({ title, description, actions }: OrderPageHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={styles.title}>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions && <div className={styles.headerActions}>{actions}</div>}
    </header>
  );
}

export function OrderPageLayout({
  sidebar,
  sidebarCollapsed,
  children,
  style,
  ...header
}: OrderPageHeaderProps & {
  sidebar: ReactNode;
  sidebarCollapsed: boolean;
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <section
      className={`${styles.page} ${sidebarCollapsed ? styles.pageTimeCollapsed : ""}`}
      style={style}
    >
      <div className={styles.sidebar}>{sidebar}</div>
      <div className={styles.content}>
        <OrderPageHeader {...header} />
        {children}
      </div>
    </section>
  );
}

export function OrderListWorkspace({ children }: { children: ReactNode }) {
  return (
    <div className={styles.workspace}>
      <div className={styles.tablePanel}>{children}</div>
    </div>
  );
}

export function OrderToolbar({
  search,
  children,
}: {
  search: InputHTMLAttributes<HTMLInputElement> & { "aria-label": string };
  children?: ReactNode;
}) {
  return (
    <div className={styles.toolbar}>
      <label className={styles.searchBox}>
        <Search size={20} aria-hidden="true" />
        <input {...search} />
      </label>
      {children}
    </div>
  );
}

export interface OrderStatusOption<Value extends string> {
  key: Value;
  label: string;
  count?: number | string;
  isError?: boolean;
  disabled?: boolean;
}

export function OrderStatusFilters<Value extends string>({
  options,
  value,
  onChange,
  label = "Trạng thái:",
}: {
  options: readonly OrderStatusOption<Value>[];
  value: Value;
  onChange(value: Value): void;
  label?: string;
}) {
  return (
    <div className={styles.filterGroup} role="group" aria-label={label}>
      <span className={styles.filterLabel}>{label}</span>
      <div className={styles.quickFilters}>
        {options.map((option) => (
          <button
            key={option.key}
            type="button"
            disabled={option.disabled}
            aria-pressed={value === option.key}
            className={`${styles.filterChip} ${option.isError ? styles.errorFilterChip : ""}`}
            onClick={() => onChange(option.key)}
          >
            {option.label}
            {option.count !== undefined && option.count !== null && <span>{option.count}</span>}
          </button>
        ))}
      </div>
    </div>
  );
}

export function OrderPageStates({
  kind,
  title,
  description,
  onRetry,
}: {
  kind: "loading" | "empty" | "error";
  title: ReactNode;
  description?: ReactNode;
  onRetry?: () => void;
}) {
  return (
    <div className={styles.pageState} role={kind === "error" ? "alert" : "status"}>
      <strong>{title}</strong>
      {description && <span>{description}</span>}
      {kind === "error" && onRetry && (
        <button type="button" className={styles.secondaryButton} onClick={onRetry}>
          Thử lại
        </button>
      )}
    </div>
  );
}

export interface OrderTableColumn<Row> {
  key: string;
  header: string;
  width?: string;
  align?: "left" | "center" | "right";
  cellClassName?: string;
  render(row: Row, index: number): ReactNode;
}

export function OrderDataTable<Row>({
  columns,
  rows,
  getRowKey,
  onRowClick,
  isRowActive,
  loading,
  error,
  loadingTitle,
  loadingDescription,
  errorTitle,
  emptyTitle,
  emptyDescription,
  pagination,
  minWidth = 720,
  caption,
  onRetry,
}: {
  columns: readonly OrderTableColumn<Row>[];
  rows: readonly Row[];
  getRowKey(row: Row): string;
  onRowClick?(row: Row, index: number): void;
  isRowActive?(row: Row): boolean;
  loading: boolean;
  error?: string | null;
  loadingTitle: string;
  loadingDescription?: string;
  errorTitle: string;
  emptyTitle: string;
  emptyDescription?: string;
  pagination: ReactNode;
  minWidth?: number;
  caption: string;
  onRetry?: () => void;
}) {
  const state = loading ? "loading" : error ? "error" : rows.length === 0 ? "empty" : null;
  return (
    <div className={styles.tableCard}>
      <div className={styles.tableWrap}>
        <table className={styles.table} style={{ minWidth }} aria-busy={loading}>
          <caption className={styles.visuallyHidden}>{caption}</caption>
          <colgroup>
            {columns.map((column) => (
              <col key={column.key} style={{ width: column.width }} />
            ))}
          </colgroup>
          <thead>
            <tr>
              {columns.map((column) => (
                <th key={column.key} scope="col" style={{ textAlign: column.align }}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {state ? (
              <tr>
                <td colSpan={columns.length}>
                  <OrderPageStates
                    kind={state}
                    title={loading ? loadingTitle : error ? errorTitle : emptyTitle}
                    description={loading ? loadingDescription : error || emptyDescription}
                    onRetry={onRetry}
                  />
                </td>
              </tr>
            ) : (
              rows.map((row, index) => (
                <tr
                  key={getRowKey(row)}
                  data-active={isRowActive?.(row) || undefined}
                  tabIndex={onRowClick ? 0 : undefined}
                  onClick={onRowClick ? () => onRowClick(row, index) : undefined}
                  onKeyDown={
                    onRowClick
                      ? (event) => {
                          if (
                            event.target === event.currentTarget &&
                            (event.key === "Enter" || event.key === " ")
                          ) {
                            event.preventDefault();
                            onRowClick(row, index);
                          }
                        }
                      : undefined
                  }
                >
                  {columns.map((column) => (
                    <td
                      key={column.key}
                      className={column.cellClassName}
                      style={{ textAlign: column.align }}
                    >
                      {column.render(row, index)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      {pagination}
    </div>
  );
}

export function OrderPagination({
  page,
  recordPage = page,
  pageSize,
  totalItems,
  totalPages,
  onPageChange,
  disabled = false,
  itemLabel,
}: {
  page: number;
  recordPage?: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  onPageChange(page: number): void;
  disabled?: boolean;
  itemLabel: string;
}) {
  const pageCount = Math.max(1, totalPages);
  const { from, to } = getOrderRecordRange(recordPage, pageSize, totalItems);
  return (
    <nav className={styles.pagination} aria-label="Phân trang danh sách">
      <span>
        Hiển thị {from} - {to} trong {totalItems} {itemLabel}
      </span>
      <div className={styles.pages}>
        <button
          type="button"
          className={styles.pageButton}
          aria-label="Trang trước"
          disabled={disabled || page <= 0}
          onClick={() => onPageChange(Math.max(0, page - 1))}
        >
          <ChevronLeft size={18} aria-hidden="true" />
        </button>
        {getOrderPageNumbers(pageCount, page).map((number) => (
          <button
            type="button"
            key={number}
            className={styles.pageButton}
            aria-current={number === page ? "page" : undefined}
            disabled={disabled}
            onClick={() => onPageChange(number)}
          >
            {number + 1}
          </button>
        ))}
        {pageCount > 5 && <span>...</span>}
        <button
          type="button"
          className={styles.pageButton}
          aria-label="Trang sau"
          disabled={disabled || page >= pageCount - 1}
          onClick={() => onPageChange(Math.min(pageCount - 1, page + 1))}
        >
          <ChevronRight size={18} aria-hidden="true" />
        </button>
      </div>
    </nav>
  );
}
