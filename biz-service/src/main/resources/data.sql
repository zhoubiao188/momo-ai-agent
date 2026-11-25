INSERT INTO t_order (order_no, user_id, status, amount, channel, trace_id, created_at, updated_at, remark) VALUES
('MO20260101001', 'U10086', 'PAID',      199.00, 'alipay', 'tr-9f2a41', DATEADD('HOUR', -5, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 'demo order'),
('MO20260101002', 'U10086', 'SHIPPED',   89.90,  'wechat', 'tr-9f2a42', DATEADD('HOUR', -4, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 'demo order'),
('MO20260101003', 'U10086', 'COMPLETED', 1299.00,'alipay', 'tr-7c1b08', DATEADD('DAY', -3, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 'demo order'),
('MO20260101004', 'U10086', 'CREATED',   59.00,  'wechat', 'tr-9f2a41', DATEADD('MINUTE', -30, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 'not paid yet'),
('MO20260101005', 'U20111', 'REFUNDED',  320.00, 'card',   'tr-7c1b08', DATEADD('DAY', -10, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 'refunded'),
('MO20260101006', 'U20111', 'CLOSED',    15.00,  'wechat', 'tr-9f2a42', DATEADD('DAY', -20, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 'timeout closed');
