UPDATE order_returns
SET status = 'RECEIVED'
WHERE status = 'COMPLETED';
