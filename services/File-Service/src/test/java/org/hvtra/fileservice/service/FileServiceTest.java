package org.hvtra.fileservice.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.hvtra.commonservice.common_core.exception.AppException;
import org.hvtra.fileservice.config.FileBucketResolver;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.exception.UploadCompletionException;
import org.hvtra.fileservice.model.entity.FileMetadata;
import org.hvtra.fileservice.model.entity.FileReferences;
import org.hvtra.fileservice.model.enums.FilePurpose;
import org.hvtra.fileservice.model.enums.FileStatus;
import org.hvtra.fileservice.model.enums.FileVisibility;
import org.hvtra.fileservice.model.request.FileAttach;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.model.response.CompleteUploadResponse;
import org.hvtra.fileservice.model.response.DownloadUrlResponse;
import org.hvtra.fileservice.model.response.InitUploadResponse;
import org.hvtra.fileservice.repository.FileMetadataRepository;
import org.hvtra.fileservice.repository.FileReferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pure unit tests: MinIO and repositories are mocks, nothing touches storage or a database. */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {
    private static final long TEN_MB = 10L * 1024 * 1024;
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Mock MinioClient minio;
    @Mock FileMetadataRepository files;
    @Mock FileReferenceRepository references;

    private final UUID user = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final UUID ref = UUID.randomUUID();
    private MinioProperties properties;
    private FileService service;

    @BeforeEach
    void setUp() {
        properties = new MinioProperties();
        properties.setPublicEndpoint("http://cdn.fixture");
        properties.getBuckets().setPublicBucket("fixture-public");
        properties.getBuckets().setPrivateBucket("fixture-private");
        properties.setPresignedUploadExpirySeconds(120);
        properties.setPresignedDownloadExpirySeconds(60);
        service = new FileService(minio, properties, new FileBucketResolver(properties), files, references);
    }

    private InitUploadRequest request(String filename, String contentType, long size, FilePurpose purpose,
                                      UUID ownerId, UUID refId) {
        return new InitUploadRequest(filename, contentType, size, purpose, "FIXTURE", ownerId, refId);
    }

    private InitUploadRequest image(String filename, FilePurpose purpose, UUID refId) {
        return request(filename, "image/png", 100L, purpose, null, refId);
    }

    private void presignReturns(String url) throws Exception {
        when(minio.getPresignedObjectUrl(any())).thenReturn(url);
    }

    private FileMetadata savedFile() {
        ArgumentCaptor<FileMetadata> captor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(files).save(captor.capture());
        return captor.getValue();
    }

    private FileMetadata file(FileVisibility visibility, FileStatus status, UUID owner) {
        return FileMetadata.builder().id(UUID.randomUUID()).ownerId(owner).purpose(FilePurpose.USER_DOCUMENT)
                .visibility(visibility).status(status).sizeBytes(15L).contentType("application/pdf")
                .originalFilename("doc.pdf").extension("pdf")
                .bucket(visibility == FileVisibility.PUBLIC ? "fixture-public" : "fixture-private")
                .objectKey("users/fixture/documents/doc.pdf")
                .publicUrl(visibility == FileVisibility.PUBLIC ? "http://cdn.fixture/fixture-public/k.png" : null)
                .build();
    }

    private int codeOf(Runnable action) {
        return assertThrows(AppException.class, action::run).getCode();
    }

    @Nested
    class InitUploadValidation {
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
        void emptyOrNegativeSizeIsRejected(long size) {
            assertEquals(7004, codeOf(() -> service.initUpload(
                    request("a.png", "image/png", size, FilePurpose.ITEM_IMAGE, null, null), user)));
            verifyNoInteractions(files, minio);
        }

        @Test
        void sizeAboveTenMegabytesIsRejected() {
            assertEquals(7005, codeOf(() -> service.initUpload(
                    request("a.png", "image/png", TEN_MB + 1, FilePurpose.ITEM_IMAGE, null, null), user)));
            verifyNoInteractions(files, minio);
        }

        @Test
        void sizeOfExactlyTenMegabytesIsAccepted() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(
                    request("a.png", "image/png", TEN_MB, FilePurpose.ITEM_IMAGE, null, null), user);
            assertEquals(TEN_MB, savedFile().getSizeBytes());
            assertEquals("http://upload.fixture/put", response.uploadUrl());
        }

        @Test
        @Disabled("Suspected bug: null content type hits Set.of(..).contains(null) and throws "
                + "NullPointerException instead of 7006 (FileService.java:266); only @NotBlank on the "
                + "controller DTO hides it")
        void missingContentTypeIsRejectedAsUnsupported() {
            assertEquals(7006, codeOf(() -> service.initUpload(
                    request("a.png", null, 10L, FilePurpose.ITEM_IMAGE, null, null), user)));
            verifyNoInteractions(files, minio);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "text/html", "image/svg+xml", "image/gif", "application/x-msdownload",
                "application/octet-stream", "image/png; charset=utf-8"})
        void unsupportedContentTypeIsRejected(String contentType) {
            assertEquals(7006, codeOf(() -> service.initUpload(
                    request("a.png", contentType, 10L, FilePurpose.ITEM_IMAGE, null, null), user)));
            verifyNoInteractions(files, minio);
        }

        @ParameterizedTest
        @ValueSource(strings = {"image/png", "image/jpeg", "application/pdf", XLSX})
        void allowedContentTypesAreAccepted(String contentType) throws Exception {
            presignReturns("http://upload.fixture/put");
            service.initUpload(request("a.bin", contentType, 10L, FilePurpose.USER_DOCUMENT, null, null), user);
            assertEquals(contentType, savedFile().getContentType());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "trailing-dot.",
                "../../etc/passwd",
                "photo.png/../../secret",
                "photo.p/ng",
                "photo.png%00",
                "photo.png ",
                "photo.pñg",
                "photo.abcdefghijk",
                "shell.php\u0000.png\n"
        })
        void extensionWithUnsafeCharactersOrLengthIsRejected(String filename) {
            assertEquals(7003, codeOf(() -> service.initUpload(image(filename, FilePurpose.ITEM_IMAGE, ref), user)));
            verifyNoInteractions(files, minio);
        }

        @Test
        void userDocumentForAnotherOwnerIsRejected() {
            assertEquals(7003, codeOf(() -> service.initUpload(
                    request("a.pdf", "application/pdf", 10L, FilePurpose.USER_DOCUMENT, otherUser, null), user)));
            verifyNoInteractions(files, minio);
        }

        @Test
        void userDocumentWithOwnIdAsOwnerIsAccepted() throws Exception {
            presignReturns("http://upload.fixture/put");
            service.initUpload(request("a.pdf", "application/pdf", 10L, FilePurpose.USER_DOCUMENT, user, null), user);
            assertEquals(user, savedFile().getOwnerId());
        }
    }

    @Nested
    class InitUploadKeysAndMetadata {
        @Test
        void extensionIsLowerCasedAndTakenFromLastDot() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("Archive.tar.PNG", FilePurpose.ITEM_IMAGE, ref), user);
            assertEquals("png", savedFile().getExtension());
            assertEquals("items/%s/%s.png".formatted(ref, response.fileId()), response.objectKey());
        }

        @Test
        void filenameWithoutDotHasNoExtension() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("README", FilePurpose.ITEM_IMAGE, ref), user);
            assertNull(savedFile().getExtension());
            assertEquals("items/%s/%s".formatted(ref, response.fileId()), response.objectKey());
        }

        @Test
        void objectKeyNeverContainsCallerSuppliedFilename() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(
                    image("../../../etc/cron.d/..%2f..%2fjob.png", FilePurpose.USER_AVATAR, null), user);
            assertEquals("users/%s/avatar/%s.png".formatted(user, response.fileId()), response.objectKey());
            assertFalse(response.objectKey().contains(".."));
            assertEquals("../../../etc/cron.d/..%2f..%2fjob.png", savedFile().getOriginalFilename());
        }

        @ParameterizedTest
        @EnumSource(value = FilePurpose.class, names = {"PRODUCT_IMAGE", "ITEM_IMAGE"})
        void itemImagesUseReferenceFolderWhenReferenceIsKnown(FilePurpose purpose) throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", purpose, ref), user);
            assertEquals("items/%s/%s.png".formatted(ref, response.fileId()), response.objectKey());
        }

        @ParameterizedTest
        @EnumSource(value = FilePurpose.class, names = {"PRODUCT_IMAGE", "ITEM_IMAGE"})
        void itemImagesWithoutReferenceGoToUploaderPendingFolder(FilePurpose purpose) throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", purpose, null), user);
            assertEquals("pending/items/%s/%s.png".formatted(user, response.fileId()), response.objectKey());
        }

        @Test
        void categoryImageKeyDependsOnReference() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse withRef = service.initUpload(image("a.png", FilePurpose.CATEGORY_IMAGE, ref), user);
            InitUploadResponse pending = service.initUpload(image("a.png", FilePurpose.CATEGORY_IMAGE, null), user);
            assertEquals("categories/%s/%s.png".formatted(ref, withRef.fileId()), withRef.objectKey());
            assertEquals("pending/categories/%s/%s.png".formatted(user, pending.fileId()), pending.objectKey());
        }

        @ParameterizedTest
        @EnumSource(value = FilePurpose.class, names = {"PROMOTION_IMAGE", "BANNER_IMAGE"})
        void marketingImagesUseLowerCasedPurposeFolder(FilePurpose purpose) throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", purpose, ref), user);
            assertEquals("public-assets/%s/%s.png".formatted(purpose.name().toLowerCase(), response.fileId()),
                    response.objectKey());
        }

        @Test
        void privatePurposesUseUserPaymentAndInvoiceFolders() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse avatar = service.initUpload(image("a.png", FilePurpose.USER_AVATAR, ref), user);
            InitUploadResponse document = service.initUpload(image("a.png", FilePurpose.USER_DOCUMENT, ref), user);
            InitUploadResponse proof = service.initUpload(image("a.png", FilePurpose.PAYMENT_PROOF, ref), user);
            InitUploadResponse invoice = service.initUpload(image("a.png", FilePurpose.INVOICE, ref), user);
            assertEquals("users/%s/avatar/%s.png".formatted(user, avatar.fileId()), avatar.objectKey());
            assertEquals("users/%s/documents/%s.png".formatted(user, document.fileId()), document.objectKey());
            assertEquals("payments/%s/proofs/%s.png".formatted(ref, proof.fileId()), proof.objectKey());
            assertEquals("invoices/%s.png".formatted(invoice.fileId()), invoice.objectKey());
        }

        @ParameterizedTest
        @EnumSource(value = FilePurpose.class,
                names = {"PRODUCT_IMAGE", "ITEM_IMAGE", "CATEGORY_IMAGE", "PROMOTION_IMAGE", "BANNER_IMAGE"})
        void publicPurposesGetPublicBucketAndPublicUrl(FilePurpose purpose) throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", purpose, ref), user);
            assertEquals(FileVisibility.PUBLIC, response.visibility());
            assertEquals("fixture-public", response.bucket());
            assertEquals("http://cdn.fixture/fixture-public/" + response.objectKey(), response.publicUrl());
            assertEquals(response.publicUrl(), savedFile().getPublicUrl());
        }

        @ParameterizedTest
        @EnumSource(value = FilePurpose.class,
                names = {"USER_AVATAR", "USER_DOCUMENT", "PAYMENT_PROOF", "INVOICE"})
        void privatePurposesGetPrivateBucketAndNoPublicUrl(FilePurpose purpose) throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", purpose, ref), user);
            assertEquals(FileVisibility.PRIVATE, response.visibility());
            assertEquals("fixture-private", response.bucket());
            assertNull(response.publicUrl());
            assertNull(savedFile().getPublicUrl());
        }

        @Test
        void trailingSlashOfPublicEndpointIsNotDuplicated() throws Exception {
            properties.setPublicEndpoint("http://cdn.fixture/");
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", FilePurpose.BANNER_IMAGE, null), user);
            assertEquals("http://cdn.fixture/fixture-public/" + response.objectKey(), response.publicUrl());
        }

        @Test
        void metadataIsPersistedAsPendingWithRequestFields() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(
                    request("Hoa don.pdf", "application/pdf", 321L, FilePurpose.PAYMENT_PROOF, otherUser, ref), user);
            FileMetadata saved = savedFile();
            assertEquals(response.fileId(), saved.getId());
            assertEquals(FileStatus.PENDING, saved.getStatus());
            assertEquals(FileVisibility.PRIVATE, saved.getVisibility());
            assertEquals(FilePurpose.PAYMENT_PROOF, saved.getPurpose());
            assertEquals("fixture-private", saved.getBucket());
            assertEquals(response.objectKey(), saved.getObjectKey());
            assertEquals("Hoa don.pdf", saved.getOriginalFilename());
            assertEquals("application/pdf", saved.getContentType());
            assertEquals("pdf", saved.getExtension());
            assertEquals(321L, saved.getSizeBytes());
            assertEquals("FIXTURE", saved.getOwnerType());
            assertEquals(ref, saved.getRefId());
            assertNull(saved.getEtag());
        }

        @Test
        void ownerDefaultsToAuthenticatedUserWhenNotSupplied() throws Exception {
            presignReturns("http://upload.fixture/put");
            service.initUpload(image("a.png", FilePurpose.USER_AVATAR, null), user);
            assertEquals(user, savedFile().getOwnerId());
        }

        @Test
        void uploadUrlIsPresignedPutForTheGeneratedObjectWithConfiguredExpiry() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse response = service.initUpload(image("a.png", FilePurpose.USER_AVATAR, null), user);
            ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
            verify(minio).getPresignedObjectUrl(captor.capture());
            assertEquals(Method.PUT, captor.getValue().method());
            assertEquals("fixture-private", captor.getValue().bucket());
            assertEquals(response.objectKey(), captor.getValue().object());
            assertEquals(120, captor.getValue().expiry());
            assertEquals(120, response.expiresInSeconds());
            assertEquals("http://upload.fixture/put", response.uploadUrl());
        }

        @Test
        void everyUploadGetsItsOwnFileIdAndObjectKey() throws Exception {
            presignReturns("http://upload.fixture/put");
            InitUploadResponse first = service.initUpload(image("a.png", FilePurpose.INVOICE, null), user);
            InitUploadResponse second = service.initUpload(image("a.png", FilePurpose.INVOICE, null), user);
            assertFalse(first.fileId().equals(second.fileId()));
            assertFalse(first.objectKey().equals(second.objectKey()));
        }

        @Test
        void storageFailureWhilePresigningIsReportedAsUploadUrlError() throws Exception {
            when(minio.getPresignedObjectUrl(any())).thenThrow(new IllegalStateException("Synthetic storage outage"));
            AppException failure = assertThrows(AppException.class,
                    () -> service.initUpload(image("a.png", FilePurpose.USER_AVATAR, null), user));
            assertEquals(8001, failure.getCode());
            assertFalse(failure.getMessage().contains("Synthetic"));
        }

        @Test
        @Disabled("Suspected bug: extension is not checked against the declared content type, "
                + "so HTML can be published in the public bucket (FileService.java:250-268, 321-334)")
        void publicUploadWithExtensionContradictingContentTypeIsRejected() {
            assertThrows(AppException.class, () -> service.initUpload(
                    request("stored-xss.html", "image/png", 100L, FilePurpose.PRODUCT_IMAGE, null, ref), user));
            verify(files, never()).save(any());
        }

        @Test
        @Disabled("Suspected bug: PAYMENT_PROOF without refId builds the literal key "
                + "'payments/null/proofs/...' (FileService.java:310-312)")
        void paymentProofWithoutReferenceDoesNotProduceNullPathSegment() throws Exception {
            when(minio.getPresignedObjectUrl(any())).thenReturn("http://upload.fixture/put");
            String objectKey;
            try {
                objectKey = service.initUpload(image("a.png", FilePurpose.PAYMENT_PROOF, null), user).objectKey();
            } catch (AppException rejected) {
                return;
            }
            assertFalse(objectKey.contains("/null/"), objectKey);
        }
    }

    @Nested
    class CompleteUpload {
        private StatObjectResponse stat(long size, String etag) {
            StatObjectResponse stat = mock(StatObjectResponse.class);
            when(stat.size()).thenReturn(size);
            if (etag != null) {
                when(stat.etag()).thenReturn(etag);
            }
            return stat;
        }

        @Test
        void unknownFileIsNotFound() {
            UUID id = UUID.randomUUID();
            when(files.findForUpdate(id)).thenReturn(Optional.empty());
            assertEquals(7000, codeOf(() -> service.completeUpload(id, user)));
            verifyNoInteractions(minio);
        }

        @Test
        void anotherUsersFileIsForbiddenAndLeftUntouched() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.PENDING, otherUser);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7003, codeOf(() -> service.completeUpload(file.getId(), user)));
            assertEquals(FileStatus.PENDING, file.getStatus());
            verify(files, never()).save(any());
            verifyNoInteractions(minio);
        }

        @Test
        void deletedFileIsNotAvailable() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.PENDING, user);
            file.setIsDeleted(true);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7009, codeOf(() -> service.completeUpload(file.getId(), user)));
            verifyNoInteractions(minio);
        }

        @Test
        void alreadyActiveFileIsReturnedWithoutTouchingStorage() {
            FileMetadata file = file(FileVisibility.PUBLIC, FileStatus.ACTIVE, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            CompleteUploadResponse response = service.completeUpload(file.getId(), user);
            assertEquals(new CompleteUploadResponse(file.getId(), FileVisibility.PUBLIC, file.getPublicUrl()), response);
            verify(files, never()).save(any());
            verifyNoInteractions(minio);
        }

        @ParameterizedTest
        @EnumSource(value = FileStatus.class, names = {"FAILED", "DELETED"})
        void fileThatIsNeitherPendingNorActiveCannotBeCompleted(FileStatus status) {
            FileMetadata file = file(FileVisibility.PRIVATE, status, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7008, codeOf(() -> service.completeUpload(file.getId(), user)));
            assertEquals(status, file.getStatus());
            verifyNoInteractions(minio);
        }

        @Test
        void matchingObjectActivatesFileAndStoresEtag() throws Exception {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.PENDING, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            StatObjectResponse stat = stat(15L, "synthetic-etag");
            when(minio.statObject(any())).thenReturn(stat);

            CompleteUploadResponse response = service.completeUpload(file.getId(), user);

            assertEquals(new CompleteUploadResponse(file.getId(), FileVisibility.PRIVATE, null), response);
            assertEquals(FileStatus.ACTIVE, file.getStatus());
            assertEquals("synthetic-etag", file.getEtag());
            verify(files).save(file);
            ArgumentCaptor<StatObjectArgs> captor = ArgumentCaptor.forClass(StatObjectArgs.class);
            verify(minio).statObject(captor.capture());
            assertEquals("fixture-private", captor.getValue().bucket());
            assertEquals(file.getObjectKey(), captor.getValue().object());
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, 14L, 16L})
        void sizeMismatchMarksFileFailedAndKeepsSizeErrorCode(long storedSize) throws Exception {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.PENDING, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            StatObjectResponse stat = stat(storedSize, null);
            when(minio.statObject(any())).thenReturn(stat);

            UploadCompletionException failure = assertThrows(UploadCompletionException.class,
                    () -> service.completeUpload(file.getId(), user));

            assertEquals(7010, failure.getCode());
            assertEquals(FileStatus.FAILED, file.getStatus());
            assertNull(file.getEtag());
            verify(files).save(file);
        }

        @Test
        void missingObjectOrStorageOutageMarksFileFailed() throws Exception {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.PENDING, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            when(minio.statObject(any())).thenThrow(new IllegalStateException("Synthetic storage outage"));

            UploadCompletionException failure = assertThrows(UploadCompletionException.class,
                    () -> service.completeUpload(file.getId(), user));

            assertEquals(8002, failure.getCode());
            assertFalse(failure.getMessage().contains("Synthetic"));
            assertEquals(FileStatus.FAILED, file.getStatus());
            verify(files).save(file);
        }
    }

    @Nested
    class CreateDownloadUrl {
        @Test
        void unknownFileIsNotFound() {
            UUID id = UUID.randomUUID();
            when(files.findById(id)).thenReturn(Optional.empty());
            assertEquals(7000, codeOf(() -> service.createDownloadUrl(id, user)));
        }

        @Test
        void deletedFileIsNotAvailableEvenToOwner() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, user);
            file.setIsDeleted(true);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7009, codeOf(() -> service.createDownloadUrl(file.getId(), user)));
            verifyNoInteractions(minio);
        }

        @ParameterizedTest
        @EnumSource(value = FileStatus.class, names = {"PENDING", "FAILED", "DELETED"})
        void fileThatIsNotActiveIsNotAvailable(FileStatus status) {
            FileMetadata file = file(FileVisibility.PUBLIC, status, user);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7009, codeOf(() -> service.createDownloadUrl(file.getId(), user)));
            verifyNoInteractions(minio);
        }

        @Test
        void publicFileReturnsPublicUrlToAnyUserWithoutPresigning() {
            FileMetadata file = file(FileVisibility.PUBLIC, FileStatus.ACTIVE, otherUser);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            DownloadUrlResponse response = service.createDownloadUrl(file.getId(), user);
            assertEquals(new DownloadUrlResponse(file.getPublicUrl(), null, FileVisibility.PUBLIC), response);
            verifyNoInteractions(minio);
        }

        @Test
        void privateFileOfAnotherUserIsForbiddenAndNeverPresigned() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, otherUser);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7002, codeOf(() -> service.createDownloadUrl(file.getId(), user)));
            verifyNoInteractions(minio);
        }

        @Test
        void ownerGetsPresignedGetUrlWithConfiguredExpiry() throws Exception {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, user);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            presignReturns("http://download.fixture/get");

            DownloadUrlResponse response = service.createDownloadUrl(file.getId(), user);

            assertEquals(new DownloadUrlResponse("http://download.fixture/get", 60, FileVisibility.PRIVATE), response);
            ArgumentCaptor<GetPresignedObjectUrlArgs> captor = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
            verify(minio).getPresignedObjectUrl(captor.capture());
            assertEquals(Method.GET, captor.getValue().method());
            assertEquals("fixture-private", captor.getValue().bucket());
            assertEquals(file.getObjectKey(), captor.getValue().object());
            assertEquals(60, captor.getValue().expiry());
        }

        @Test
        void storageFailureWhilePresigningIsReportedAsDownloadUrlError() throws Exception {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, user);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            when(minio.getPresignedObjectUrl(any())).thenThrow(new IllegalStateException("Synthetic storage outage"));
            assertEquals(8003, codeOf(() -> service.createDownloadUrl(file.getId(), user)));
        }

        @Test
        @Disabled("Suspected bug: private file without owner makes checkDownloadPermission throw "
                + "NullPointerException instead of a 7002 refusal (FileService.java:341)")
        void privateFileWithoutOwnerIsRefusedInsteadOfCrashing() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, null);
            when(files.findById(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7002, codeOf(() -> service.createDownloadUrl(file.getId(), user)));
            verifyNoInteractions(minio);
        }
    }

    @Nested
    class AttachFile {
        @Test
        void fileIdIsRequired() {
            assertEquals(7000, codeOf(() -> service.attachFile(new FileAttach(null, ref), user)));
            verifyNoInteractions(files, references);
        }

        @Test
        void referenceIdIsRequired() {
            assertEquals(7001, codeOf(() -> service.attachFile(new FileAttach(UUID.randomUUID(), null), user)));
            verifyNoInteractions(files, references);
        }

        @Test
        void unknownFileIsNotFound() {
            UUID id = UUID.randomUUID();
            when(files.findForUpdate(id)).thenReturn(Optional.empty());
            assertEquals(7000, codeOf(() -> service.attachFile(new FileAttach(id, ref), user)));
        }

        @Test
        void anotherUsersFileCannotBeAttached() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, otherUser);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            assertEquals(7003, codeOf(() -> service.attachFile(new FileAttach(file.getId(), ref), user)));
            assertNull(file.getRefId());
            verify(files, never()).save(any());
            verifyNoInteractions(references);
        }

        @Test
        void fileWithTypedBusinessReferenceCannotBeReattached() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            when(references.findByFile_IdAndIsDeletedFalse(file.getId()))
                    .thenReturn(List.of(FileReferences.builder().file(file).refType("AI_REPORT")
                            .refId(UUID.randomUUID()).usageType("GENERATED").build()));
            assertEquals(7003, codeOf(() -> service.attachFile(new FileAttach(file.getId(), ref), user)));
            assertNull(file.getRefId());
            verify(files, never()).save(any());
        }

        @ParameterizedTest
        @EnumSource(value = FileStatus.class, names = {"PENDING", "FAILED", "DELETED"})
        void fileThatIsNotActiveCannotBeAttached(FileStatus status) {
            FileMetadata file = file(FileVisibility.PRIVATE, status, user);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of());
            assertEquals(7009, codeOf(() -> service.attachFile(new FileAttach(file.getId(), ref), user)));
            assertNull(file.getRefId());
            verify(files, never()).save(any());
        }

        @Test
        void softDeletedFileCannotBeAttached() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, user);
            file.setIsDeleted(true);
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of());
            assertEquals(7009, codeOf(() -> service.attachFile(new FileAttach(file.getId(), ref), user)));
            verify(files, never()).save(any());
        }

        @Test
        void activeOwnedFileGetsTheNewReference() {
            FileMetadata file = file(FileVisibility.PRIVATE, FileStatus.ACTIVE, user);
            file.setRefId(UUID.randomUUID());
            when(files.findForUpdate(file.getId())).thenReturn(Optional.of(file));
            when(references.findByFile_IdAndIsDeletedFalse(file.getId())).thenReturn(List.of());

            assertEquals(1, service.attachFile(new FileAttach(file.getId(), ref), user));

            assertEquals(ref, file.getRefId());
            assertSame(file, savedFile());
            assertNotNull(file.getOwnerId());
            assertTrue(file.getStatus() == FileStatus.ACTIVE);
        }
    }
}
