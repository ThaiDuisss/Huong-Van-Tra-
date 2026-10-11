"""Read-only preflight. No migration or DDL is executed by this script."""
import argparse
import json

import pymysql

from app.core.settings import Settings


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--env-file", required=True)
    args = parser.parse_args()
    settings = Settings(_env_file=args.env_file)
    print(json.dumps({"configuredSchema": settings.mysql_database, "host": settings.mysql_host}))
    try:
        connection = pymysql.connect(host=settings.mysql_host, port=settings.mysql_port,
            user=settings.mysql_user, password=settings.mysql_password.get_secret_value(),
            database=settings.mysql_database, connect_timeout=5, read_timeout=5, write_timeout=5,
            autocommit=False)
        with connection:
            with connection.cursor() as cursor:
                cursor.execute("SET TRANSACTION READ ONLY")
                cursor.execute("START TRANSACTION")
                cursor.execute("SELECT DATABASE()")
                print(json.dumps({"actualSchema": cursor.fetchone()[0]}))
                cursor.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('flyway_schema_history', 'ai_reports', 'ai_report_imports')")
                tables = [row[0] for row in cursor.fetchall()]
                print(json.dumps({"tables": tables}))
                if "flyway_schema_history" in tables:
                    cursor.execute("SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank")
                    print(json.dumps({"history": cursor.fetchall()}))
            connection.rollback()
    except pymysql.MySQLError as error:
        # Do not print connection strings, SQL parameters, passwords or tokens.
        print(json.dumps({"connection": "unavailable", "errorType": type(error).__name__,
            "errorCode": error.args[0] if error.args and isinstance(error.args[0], int) else None}))
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
