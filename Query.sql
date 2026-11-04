SHOW VARIABLES
    WHERE Variable_name IN (
                            'general_log',
                            'log_output',
                            'log_bin',
                            'binlog_expire_logs_seconds'
        );

SELECT
    COUNT(*) AS log_count,
    MIN(event_time) AS oldest_log,
    MAX(event_time) AS newest_log
FROM mysql.general_log;