"""Owned local Docker MySQL only. Never targets recommend.env or a supplied host.

Run from the service's venv. Requires an external SELECT-only shared preflight
JSON (identity.server_uuid), to reject any accidental shared-server identity.
Credentials stay in a private temporary directory and are never printed.
"""
import argparse
from dataclasses import dataclass, field
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
import unittest
from uuid import uuid4

import pymysql
from sqlalchemy import create_engine
from sqlalchemy.engine import URL

SERVICE = Path(__file__).resolve().parents[1]
LABEL = "org.hvtra.ingredient-poc-test"


def file_hash(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


@dataclass(repr=False)
class IsolatedMySQL:
    port: int
    password: str = field(repr=False)
    databases: tuple[str, str]
    server_uuid: str

    def __post_init__(self):
        if not 1024 <= self.port <= 65535 or len(self.databases) != 2:
            raise ValueError("invalid isolated endpoint")
        if not all(re.fullmatch(r"ingredient_poc_(fresh|upgrade)_[0-9a-f]{12}", d) for d in self.databases):
            raise ValueError("only owned research test databases are allowed")

    def connect(self, database, *, autocommit=False, dict_rows=True):
        if database not in self.databases:
            raise ValueError("database is outside owned test scope")
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
        tls.check_hostname = False
        tls.verify_mode = ssl.CERT_NONE  # Owned loopback test instance, not shared TLS.
        c = pymysql.connect(host="127.0.0.1", port=self.port, user="root",
                            password=self.password, database=database, ssl=tls,
                            connect_timeout=5, read_timeout=12, write_timeout=12,
                            autocommit=autocommit, cursorclass=(
                                pymysql.cursors.DictCursor if dict_rows else pymysql.cursors.Cursor))
        with c.cursor(pymysql.cursors.DictCursor) as q:
            q.execute("SELECT DATABASE() AS db,@@server_uuid AS server_uuid,VERSION() AS version")
            r = q.fetchone()
            if r["db"] != database or r["server_uuid"] != self.server_uuid or not r["version"].startswith("8.4."):
                c.close()
                raise ValueError("test identity mismatch; writes denied")
            q.execute("SET SESSION innodb_lock_wait_timeout=3")
            q.execute("SET SESSION lock_wait_timeout=5")
        return c

    def engine(self, database):
        if database not in self.databases:
            raise ValueError("database outside test scope")
        return create_engine(URL.create("mysql+pymysql", username="root", password=self.password,
                                        host="127.0.0.1", port=self.port, database=database),
                             creator=lambda: self.connect(database, dict_rows=False),
                             echo=False, pool_pre_ping=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shared-preflight", type=Path, required=True)
    parser.add_argument("--docker-context", default="desktop-linux")
    parser.add_argument("--image", default="mysql:8.4")
    parser.add_argument("--output-root", type=Path, default=Path(tempfile.gettempdir()))
    parser.add_argument("--synthetic", action="store_true",
                        help="also generate/validate/import/publish the full Phase 2 research fixture")
    args = parser.parse_args()
    root = SERVICE.parents[1]
    if args.output_root.resolve() == root or root in args.output_root.resolve().parents:
        raise ValueError("test artifacts must be outside the repository")
    shared = json.loads(args.shared_preflight.read_text(encoding="utf-8"))
    assert shared["identity"][0]["db"] == "recommend_app"
    shared_uuid = shared["identity"][0]["server_uuid"]
    tag = uuid4().hex[:12]
    output = Path(tempfile.mkdtemp(prefix="hvtra-poc-mysql-" + tag + "-", dir=args.output_root))
    password = secrets.token_hex(24)
    docker = ["docker", "--context", args.docker_context]
    env = {k: v for k, v in os.environ.items()
           if not k.startswith(("FLYWAY_", "MYSQL_", "SPRING_DATASOURCE_", "DOCKER_HOST", "DOCKER_CONTEXT"))}
    evidence = {"run_id": tag, "phase2_executed": False, "production_writes": 0,
                "flyway": [], "isolation": {}, "cleanup": {}}
    network = "hvtra-poc-test-" + tag
    container = "hvtra-poc-mysql-" + tag
    owned_network = owned_container = False

    def redact(value):
        return value.replace(password, "[REDACTED_TEST_PASSWORD]")

    def run(command, *, timeout=120, check=True):
        r = subprocess.run(command, cwd=SERVICE, env=env, text=True,
                           capture_output=True, timeout=timeout, encoding="utf-8", errors="replace")
        if check and r.returncode:
            raise RuntimeError(redact(r.stdout + "\n" + r.stderr)[-10000:])
        return r

    def inspect_container():
        return json.loads(run(docker + ["inspect", container]).stdout)[0]

    def verify_owned():
        i = inspect_container()
        evidence["last_container_gate"] = {
            "state": i["State"]["Status"], "owned_label": i["Config"]["Labels"].get(LABEL) == tag,
            "binds_present": bool(i["HostConfig"].get("Binds")),
            "tmpfs": i["HostConfig"].get("Tmpfs"), "mount_types": [m["Type"] for m in i["Mounts"]],
            "networks": list(i["NetworkSettings"]["Networks"]), "ports": i["NetworkSettings"]["Ports"],
            "requested_bindings": i["HostConfig"].get("PortBindings"),
        }
        assert i["Config"]["Labels"].get(LABEL) == tag, "container ownership mismatch"
        assert not i["HostConfig"].get("Binds"), "host binds denied"
        assert i["HostConfig"]["Tmpfs"].get("/var/lib/mysql"), "data tmpfs missing"
        assert all(m["Type"] == "tmpfs" for m in i["Mounts"]), "persistent/bind mounts denied"
        assert set(i["NetworkSettings"]["Networks"]) == {network}, "foreign network attached"
        n = json.loads(run(docker + ["network", "inspect", network]).stdout)[0]
        assert n["Driver"] == "bridge" and n["Labels"].get(LABEL) == tag, "network is not an owned bridge"
        assert n["Options"].get("com.docker.network.bridge.host_binding_ipv4") == "127.0.0.1", "network default is not loopback"
        assert n["Options"].get("com.docker.network.bridge.enable_icc") == "false", "inter-container access must be disabled"
        bindings = i["NetworkSettings"]["Ports"]["3306/tcp"]
        if not bindings:
            raise KeyError("port binding not ready")
        assert len(bindings) == 1 and bindings[0]["HostIp"] == "127.0.0.1", "non-loopback binding denied"
        assert all(not v for k, v in i["NetworkSettings"]["Ports"].items() if k != "3306/tcp"), "extra exposed port denied"
        return i, int(bindings[0]["HostPort"])

    try:
        context = json.loads(run(docker + ["context", "inspect", args.docker_context]).stdout)[0]
        endpoint = context["Endpoints"]["docker"]["Host"]
        assert endpoint.startswith(("npipe:////./pipe/", "unix:///")), "remote Docker denied"
        assert run(docker + ["info", "--format", "{{.OSType}}"]).stdout.strip() == "linux"
        evidence["isolation"]["docker_server_version"] = run(
            docker + ["version", "--format", "{{.Server.Version}}"]).stdout.strip()
        image = run(docker + ["image", "inspect", args.image], check=False)
        if image.returncode:
            run(docker + ["pull", args.image], timeout=300)
        image_info = json.loads(run(docker + ["image", "inspect", args.image]).stdout)[0]
        assert image_info["Os"] == "linux"
        evidence["isolation"].update(docker_endpoint=endpoint, image_id=image_info["Id"],
                                     image_digests=image_info.get("RepoDigests", []))
        run(docker + ["network", "create", "--driver", "bridge",
                      "--opt", "com.docker.network.bridge.host_binding_ipv4=127.0.0.1",
                      "--opt", "com.docker.network.bridge.enable_icc=false",
                      "--label", LABEL + "=" + tag, network])
        owned_network = True
        secret_file = output / "private-test.env"
        secret_file.write_text("MYSQL_ROOT_PASSWORD=" + password + "\nMYSQL_ROOT_HOST=%\n", encoding="ascii")
        run(docker + ["create", "--name", container, "--label", LABEL + "=" + tag,
                      "--network", network, "--publish", "127.0.0.1::3306",
                      "--tmpfs", "/var/lib/mysql:rw,size=536870912",
                      "--memory", "1g", "--cpus", "2", "--env-file", str(secret_file),
                      image_info["Id"], "--innodb-buffer-pool-size=64M",
                      "--innodb-redo-log-capacity=67108864", "--performance-schema=OFF"])
        owned_container = True
        run(docker + ["start", container])
        deadline = time.monotonic() + 100
        server = None
        while time.monotonic() < deadline:
            try:
                i, port = verify_owned()
                tls = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
                tls.check_hostname = False
                tls.verify_mode = ssl.CERT_NONE
                c = pymysql.connect(host="127.0.0.1", port=port, user="root", password=password,
                                    ssl=tls, connect_timeout=2, read_timeout=3, autocommit=False)
                with c.cursor() as q:
                    q.execute("SELECT VERSION(),@@server_uuid")
                    version, server_uuid = q.fetchone()
                c.rollback()
                c.close()
                assert version.startswith("8.4."), "image did not run MySQL 8.4"
                assert server_uuid != shared_uuid, "test server UUID matches shared server"
                server = (port, version, server_uuid)
                break
            except (pymysql.MySQLError, KeyError, TypeError):
                time.sleep(1)
        if server is None:
            logs = redact(run(docker + ["logs", "--tail", "50", container], check=False).stderr)
            raise RuntimeError("owned MySQL startup failed: " + logs)
        port, version, server_uuid = server
        databases = ("ingredient_poc_fresh_" + tag, "ingredient_poc_upgrade_" + tag)
        evidence["isolation"].update(container_id=i["Id"], network=network, internal_network=False,
                                     dedicated_bridge=True, inter_container_communication=False,
                                     tmpfs=True, loopback_host="127.0.0.1", port=port,
                                     mysql_version=version, server_uuid=server_uuid,
                                     shared_server_uuid_distinct=True, databases=databases)
        # First DDL is allowed only after Docker ownership/identity gates above.
        c = pymysql.connect(host="127.0.0.1", port=port, user="root", password=password,
                            ssl=tls, autocommit=False)
        with c.cursor() as q:
            for db in databases:
                assert db.startswith("ingredient_poc_") and db.replace("_", "").isalnum()
                q.execute("CREATE DATABASE " + db + " CHARACTER SET utf8mb4 COLLATE utf8mb4_bin")
        c.close()
        fixture = IsolatedMySQL(port, password, databases, server_uuid)
        flyway = shutil.which("flyway")
        if not flyway:
            raise RuntimeError("Flyway CLI is unavailable")
        locations = (SERVICE / "flyway/sql").as_posix()

        def migrate(database, *, target=None, label):
            verify_owned()
            with fixture.connect(database) as conn:
                conn.rollback()
            config = output / ("private-" + label + ".conf")
            config.write_text("\n".join([
                "flyway.url=jdbc:mysql://127.0.0.1:" + str(port) + "/" + database
                + "?sslMode=DISABLED&allowPublicKeyRetrieval=true",
                "flyway.user=root", "flyway.password=" + password,
                "flyway.schemas=" + database, "flyway.defaultSchema=" + database,
                "flyway.createSchemas=false",
                "flyway.locations=filesystem:" + locations,
                "flyway.outOfOrder=false", "flyway.validateMigrationNaming=true",
                "flyway.baselineOnMigrate=false", "flyway.cleanDisabled=true",
                *([] if target is None else ["flyway.target=" + target]),
            ]) + "\n", encoding="utf-8")
            command = [flyway, "-configFiles=" + str(config), "-outputType=json", "migrate"]
            result = run(command, timeout=180, check=False)
            out = redact(result.stdout + "\n" + result.stderr)
            (output / (label + ".log")).write_text(out, encoding="utf-8")
            record = {"label": label, "database": database, "target": target,
                      "exit_code": result.returncode, "log": label + ".log"}
            try:
                payload = json.loads(result.stdout)
            except ValueError:
                payload = {}
            for k in ["schemaName", "flywayVersion", "migrationsExecuted", "success", "targetSchemaVersion"]:
                record[k] = payload.get(k)
            evidence["flyway"].append(record)
            if result.returncode:
                message = payload.get("exception") or out[-2000:]
                raise RuntimeError("Flyway " + label + " failed: " + message)
            if payload.get("schemaName") != database:
                raise RuntimeError("Flyway schema identity mismatch")
            return record

        migrate(databases[0], label="fresh")
        migrate(databases[1], target="20261012194438", label="upgrade-baseline")
        with fixture.connect(databases[1]) as c:
            with c.cursor() as q:
                q.execute("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION")
                before = q.fetchall()
                q.execute("SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'ingredient_poc_%'")
                assert q.fetchone()["n"] == 0
            c.rollback()
        migrate(databases[1], label="upgrade-poc")
        with fixture.connect(databases[1]) as c:
            with c.cursor() as q:
                q.execute("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name NOT LIKE 'ingredient_poc_%' ORDER BY TABLE_NAME,ORDINAL_POSITION")
                assert q.fetchall() == before
            c.rollback()
        evidence["upgrade_v1_columns_unchanged"] = True
        spec = importlib.util.spec_from_file_location(
            "ingredient_poc_migration_policy", SERVICE.parents[1] / "scripts/migrations/check_migration_naming.py")
        policy = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(policy)
        evidence["flyway_history"] = {}
        for database in databases:
            with fixture.connect(database) as c:
                with c.cursor() as q:
                    q.execute("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")
                    rows = q.fetchall()
                    for row in rows:
                        assert Path(row["script"]).name == row["script"]
                        source = SERVICE / "flyway/sql" / row["script"]
                        assert row["success"] == 1
                        assert policy.flyway_checksum(source.read_text(encoding="utf-8")) == row["checksum"]
                    evidence["flyway_history"][database] = rows
                c.rollback()
        evidence["history_checksums_match_source"] = True
        evidence["migration_files_sha256"] = {
            p.name: file_hash(p) for p in sorted((SERVICE / "flyway/sql").glob("V*__*.sql"))}
        spec = importlib.util.spec_from_file_location(
            "ingredient_poc_mysql_integration", SERVICE / "tests/mysql_poc/integration.py")
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        spec.loader.exec_module(module)
        module.FIXTURE = fixture
        module.EVIDENCE = evidence
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(module))
        evidence["tests"] = {"run": result.testsRun, "failures": len(result.failures),
                             "errors": len(result.errors), "skipped": len(result.skipped)}
        successful = result.wasSuccessful()
        if args.synthetic and successful:
            spec = importlib.util.spec_from_file_location(
                "ingredient_poc_synthetic_integration", SERVICE / "tests/mysql_poc/synthetic_integration.py")
            module = importlib.util.module_from_spec(spec)
            sys.modules[spec.name] = module
            spec.loader.exec_module(module)
            module.FIXTURE, module.EVIDENCE = fixture, evidence
            module.ARTIFACT_ROOT, module.SHARED_UUID = output, shared_uuid
            evidence["phase2_executed"] = True
            result2 = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(module))
            evidence["phase2_tests"] = {"run": result2.testsRun, "failures": len(result2.failures),
                                       "errors": len(result2.errors), "skipped": len(result2.skipped)}
            successful = result2.wasSuccessful()
        evidence["status"] = "PASS" if successful else "FAIL"
        return 0 if successful else 1
    except Exception as error:
        evidence["status"] = "FAIL" if evidence["flyway"] else "BLOCKED"
        evidence["error_type"] = type(error).__name__
        evidence["error"] = redact(str(error))
        print(evidence["error"], file=sys.stderr)
        return 1
    finally:
        if owned_container:
            try:
                current = inspect_container()
                assert current["Config"]["Labels"].get(LABEL) == tag
                run(docker + ["rm", "--force", "--volumes", container])
                evidence["cleanup"]["container_removed"] = True
            except Exception as error:
                evidence["cleanup"]["container_error"] = type(error).__name__
        if owned_network:
            try:
                n = json.loads(run(docker + ["network", "inspect", network]).stdout)[0]
                assert n["Labels"].get(LABEL) == tag and not n["Containers"]
                run(docker + ["network", "rm", network])
                evidence["cleanup"]["network_removed"] = True
            except Exception as error:
                evidence["cleanup"]["network_error"] = type(error).__name__
        for path in output.glob("private-*"):
            path.unlink()  # Exact, freshly owned temporary files only.
        (output / "evidence.json").write_text(json.dumps(evidence, indent=2, default=str), encoding="utf-8")
        print(json.dumps({"status": evidence.get("status"), "evidence": str(output / "evidence.json"),
                          "cleanup": evidence["cleanup"]}))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    sys.path.insert(0, str(SERVICE))
    raise SystemExit(main())
