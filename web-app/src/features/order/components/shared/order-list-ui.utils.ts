export function getOrderPageNumbers(totalPages: number, currentPage: number) {
  const pageCount = Math.max(totalPages, 1);
  const start = Math.max(0, Math.min(currentPage - 2, pageCount - 5));
  return Array.from({ length: Math.min(pageCount, 5) }, (_, index) => start + index);
}

export function getOrderRecordRange(page: number, pageSize: number, totalItems: number) {
  return {
    from: totalItems > 0 ? page * pageSize + 1 : 0,
    to: Math.min((page + 1) * pageSize, totalItems),
  };
}
