import unittest

from check_migration_naming import flyway_checksum, is_migration, validate, verified_restorations


ROOT = "services/File-Service/src/main/resources/migration/"


class MigrationNamingTest(unittest.TestCase):
    def test_historical_formats_remain_allowed(self):
        paths = [ROOT + "V1__init.sql", ROOT + "V261005300000__legacy.sql"]
        self.assertEqual([], validate(paths, paths))

    def test_new_numeric_or_two_digit_year_versions_are_rejected(self):
        for version in ["7", "8", "261010221530"]:
            with self.subTest(version=version):
                self.assertTrue(validate([ROOT + f"V{version}__new.sql"], []))

    def test_valid_new_calendar_timestamp_is_allowed(self):
        self.assertEqual([], validate([ROOT + "V20261010221530__new.sql"], [ROOT + "V6__historical.sql"]))

    def test_invalid_calendar_timestamps_are_rejected(self):
        for version in ["20260230221530", "20261010251530", "20261310221530"]:
            with self.subTest(version=version):
                self.assertTrue(validate([ROOT + f"V{version}__new.sql"], []))

    def test_duplicate_timestamp_versions_are_rejected(self):
        paths = [ROOT + "V20261010221530__one.sql", ROOT + "V20261010221530__two.sql"]
        self.assertTrue(validate(paths, []))

    def test_equivalent_flyway_versions_are_duplicates(self):
        paths = [ROOT + "V1__one.sql", ROOT + "V01_0__two.sql"]
        self.assertTrue(validate(paths, paths))

    def test_same_version_in_different_services_is_allowed(self):
        paths = [ROOT + "V20261010221530__one.sql", "services/Other/src/main/resources/db/migration/V20261010221530__two.sql"]
        self.assertEqual([], validate(paths, []))

    def test_same_service_duplicate_across_locations_is_rejected(self):
        paths = [ROOT + "V1__one.sql", "services/File-Service/src/main/resources/db/migration/V1__two.sql"]
        self.assertTrue(validate(paths, paths))

    def test_older_new_timestamp_requires_ordering_resolution(self):
        self.assertTrue(validate([ROOT + "V20261010221530__new.sql"], [ROOT + "V20261011221530__applied.sql"]))

    def test_supported_locations_and_non_migration_sql(self):
        self.assertTrue(is_migration(ROOT + "V1__init.sql"))
        self.assertTrue(is_migration("services/Recommendation-Service/flyway/sql/V1__init.sql"))
        self.assertFalse(is_migration("scripts/database/cleanup.sql"))

    def test_verified_recovered_history_can_keep_its_original_name(self):
        path = ROOT + "V6__historical_fixture.sql"
        source = "-- historical test fixture\nSELECT 1;\n"
        restored, errors = verified_restorations([path], lambda _: source, {path: flyway_checksum(source)})
        self.assertEqual([], errors)
        self.assertEqual([], validate([path], restored))

    def test_mismatched_recovery_never_receives_a_historical_exception(self):
        path = ROOT + "V6__historical_fixture.sql"
        restored, errors = verified_restorations([path], lambda _: "SELECT 2;", {path: flyway_checksum("SELECT 1;")})
        self.assertEqual([], restored)
        self.assertTrue(errors)
        self.assertTrue(validate([path], restored))


if __name__ == "__main__":
    unittest.main()
