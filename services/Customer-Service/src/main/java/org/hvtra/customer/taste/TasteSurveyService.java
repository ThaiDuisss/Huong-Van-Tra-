package org.hvtra.customer.taste;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.hvtra.commonservice.common_core.domain.constants.Role;
import org.hvtra.commonservice.common_core.utils.SecurityUtils;
import org.hvtra.customer.dto.response.PageResponse;
import org.hvtra.customer.exception.CustomerServiceException;
import org.hvtra.customer.model.entity.Customer;
import org.hvtra.customer.model.enums.CustomerStatus;
import org.hvtra.customer.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
class TasteSurveyService {
    private static final int GUEST_LINK_HOURS = 24;
    private final TasteSurveyRepository surveys;
    private final CustomerRepository customers;
    private final TasteTaxonomyAuthority taxonomy;
    private final TasteCriteria criteria;
    private final TasteJson json;
    private final TransactionTemplate writes;

    TasteSurveyService(TasteSurveyRepository surveys, CustomerRepository customers, TasteTaxonomyAuthority taxonomy,
                       TasteCriteria criteria, TasteJson json, PlatformTransactionManager transactionManager) {
        this.surveys = surveys;
        this.customers = customers;
        this.taxonomy = taxonomy;
        this.criteria = criteria;
        this.json = json;
        this.writes = new TransactionTemplate(transactionManager);
    }

    public TasteSurveyDto.Response create(TasteSurveyDto.Create request, UUID key) {
        UUID actor = posActor();
        TasteCriteria.Selection selection = criteria.normalize(request.criteria(), true);
        Draft draft = new Draft(request.customerId(), request.originInvoiceId(), null, request.taxonomyVersion(),
                request.taxonomySha256(), request.rawDescription(), selection);
        return createDraft(draft, actor, key);
    }

    public TasteSurveyDto.Response copy(UUID sourceId, TasteSurveyDto.Copy request, UUID key) {
        UUID actor = posActor();
        TasteSurvey source = get(sourceId);
        if (!request.customerId().equals(source.getCustomerId()) || source.getStatus() != TasteSurveyStatus.SUBMITTED) {
            throw TasteSurveyErrors.notFound();
        }
        Draft draft = new Draft(request.customerId(), request.originInvoiceId(), sourceId, source.getTaxonomyVersion(),
                source.getTaxonomySha256(), source.getRawDescription(), criteria.normalize(source.getCriteria(), false));
        return createDraft(draft, actor, key);
    }

    private TasteSurveyDto.Response createDraft(Draft draft, UUID actor, UUID key) {
        String hash = createHash(draft);
        Optional<TasteSurvey> existing = surveys.findByOwnerActorIdAndCreateRequestKey(actor, key);
        if (existing.isPresent()) {
            return replayCreate(existing.get(), hash, actor);
        }

        validateTaxonomy(draft.taxonomyVersion(), draft.taxonomySha256(), draft.selection());
        try {
            return writes.execute(status -> {
                if (draft.customerId() != null) {
                    requireActiveCustomer(draft.customerId());
                }
                LocalDateTime now = now();
                TasteSurvey survey = new TasteSurvey();
                survey.setCustomerId(draft.customerId());
                survey.setOriginInvoiceId(draft.invoiceId());
                survey.setCopiedFromSurveyId(draft.copiedFrom());
                survey.setOwnerActorId(actor);
                survey.setSessionId(UUID.randomUUID());
                survey.setTaxonomyVersion(draft.taxonomyVersion());
                survey.setTaxonomySha256(draft.taxonomySha256());
                survey.setCriteria(draft.selection().document());
                survey.setCriteriaSha256(criteria.digest(draft.taxonomySha256(), draft.selection()));
                survey.setRawDescription(draft.rawDescription());
                survey.setStatus(TasteSurveyStatus.DRAFT);
                survey.setCreatedBy(actor);
                survey.setUpdatedBy(actor);
                survey.setCreatedAt(now);
                survey.setUpdatedAt(now);
                if (draft.customerId() == null) {
                    survey.setExpiresAt(now.plusHours(GUEST_LINK_HOURS));
                }
                survey.setCreateRequestKey(key);
                survey.setCreateRequestHash(hash);
                return response(surveys.saveAndFlush(survey), actor);
            });
        } catch (DataIntegrityViolationException failure) {
            // The unique actor/key can lose a concurrent insert. Read in a new transaction after rollback.
            return surveys.findByOwnerActorIdAndCreateRequestKey(actor, key)
                    .map(survey -> replayCreate(survey, hash, actor))
                    .orElseThrow(() -> failure);
        }
    }

    public TasteSurveyDto.Response update(UUID id, UUID session, long version, TasteSurveyDto.UpdateDraft request) {
        UUID actor = posActor();
        TasteSurvey draft = mutationSurvey(id, actor, session);
        requireDraft(draft, version);
        TasteCriteria.Selection selection = criteria.normalize(request.criteria(), false);
        validateTaxonomy(draft.getTaxonomyVersion(), draft.getTaxonomySha256(), selection);
        try {
            return writes.execute(status -> {
                TasteSurvey survey = mutationSurvey(id, actor, session);
                requireDraft(survey, version);
                survey.setRawDescription(request.rawDescription());
                survey.setCriteria(selection.document());
                survey.setCriteriaSha256(criteria.digest(survey.getTaxonomySha256(), selection));
                touch(survey, actor, now());
                return response(surveys.saveAndFlush(survey), actor);
            });
        } catch (OptimisticLockingFailureException failure) {
            throw TasteSurveyErrors.stale();
        }
    }

    public TasteSurveyDto.Response submit(UUID id, UUID session, long version, UUID key) {
        UUID actor = posActor();
        TasteSurvey draft = ownedSurvey(id, actor, session);
        if (draft.getStatus() == TasteSurveyStatus.SUBMITTED) {
            return replaySubmit(draft, key, actor);
        }
        requireUsable(draft);
        requireDraft(draft, version);
        TasteCriteria.Selection selection = criteria.normalize(draft.getCriteria(), false);
        if (selection.isEmpty()) {
            throw TasteSurveyErrors.invalidCriteria("Chọn ít nhất một tiêu chí trước khi chốt khảo sát");
        }
        validateTaxonomy(draft.getTaxonomyVersion(), draft.getTaxonomySha256(), selection);

        String digest = criteria.digest(draft.getTaxonomySha256(), selection);
        String hash = submitHash(draft, digest);
        try {
            return writes.execute(status -> {
                TasteSurvey survey = ownedSurvey(id, actor, session);
                if (survey.getStatus() == TasteSurveyStatus.SUBMITTED) {
                    requireSameSubmit(survey, key, hash);
                    return response(survey, actor);
                }
                requireUsable(survey);
                requireDraft(survey, version);
                LocalDateTime now = now();
                survey.setCriteria(selection.document());
                survey.setCriteriaSha256(digest);
                survey.setSubmittedAt(now);
                survey.setSubmitRequestKey(key);
                survey.setSubmitRequestHash(hash);
                survey.setStatus(TasteSurveyStatus.SUBMITTED);
                touch(survey, actor, now);
                return response(surveys.saveAndFlush(survey), actor);
            });
        } catch (OptimisticLockingFailureException failure) {
            TasteSurvey current = ownedSurvey(id, actor, session);
            if (current.getStatus() == TasteSurveyStatus.SUBMITTED) {
                requireSameSubmit(current, key, hash);
                return response(current, actor);
            }
            throw TasteSurveyErrors.stale();
        }
    }

    public TasteSurveyDto.Response link(UUID id, long version, TasteSurveyDto.Link request) {
        UUID actor = posActor();
        TasteSurvey current = linkContext(id, actor, request);
        if (current.getCustomerId() != null) {
            return replayLink(current, request.customerId(), actor);
        }
        requireUsable(current);
        requireVersion(current, version);
        int updated = Objects.requireNonNull(writes.execute(status -> {
            requireActiveCustomer(request.customerId());
            return surveys.linkGuest(id, request.customerId(), actor, request.sessionId(), request.originInvoiceId(),
                    version, current.getStatus(), now());
        }));

        TasteSurvey result = linkContext(id, actor, request);
        if (updated == 1 || result.getCustomerId() != null) {
            return replayLink(result, request.customerId(), actor);
        }
        requireUsable(result);
        throw TasteSurveyErrors.stale();
    }

    @Transactional(readOnly = true)
    public TasteSurveyDto.Response detail(UUID id, UUID customerId, UUID session) {
        UUID actor = posActor();
        TasteSurvey survey = get(id);
        if (survey.getCustomerId() == null) {
            if (customerId != null) {
                throw TasteSurveyErrors.notFound();
            }
            requireOwner(survey, actor, session);
            requireUsable(survey);
        } else {
            if (!survey.getCustomerId().equals(customerId)) {
                throw TasteSurveyErrors.notFound();
            }
            requireExistingCustomer(customerId);
            if (survey.getStatus() != TasteSurveyStatus.SUBMITTED) {
                requireOwner(survey, actor, session);
                requireUsable(survey);
            }
        }
        return response(survey, actor);
    }

    @Transactional(readOnly = true)
    public TasteSurveyDto.Response latest(UUID customerId) {
        UUID actor = posActor();
        requireExistingCustomer(customerId);
        return surveys.findFirstByCustomerIdAndStatusOrderBySubmittedAtDescIdDesc(customerId, TasteSurveyStatus.SUBMITTED)
                .map(survey -> response(survey, actor)).orElseThrow(TasteSurveyErrors::notFound);
    }

    @Transactional(readOnly = true)
    public PageResponse<TasteSurveyDto.Response> history(UUID customerId, int page, int size) {
        UUID actor = posActor();
        requireExistingCustomer(customerId);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw TasteSurveyErrors.invalid("Tham số phân trang lịch sử khảo sát không hợp lệ");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt", "id"));
        return PageResponse.from(surveys.findByCustomerIdAndStatus(customerId, TasteSurveyStatus.SUBMITTED, pageable)
                .map(survey -> response(survey, actor)));
    }

    private TasteSurvey linkContext(UUID id, UUID actor, TasteSurveyDto.Link request) {
        TasteSurvey survey = get(id);
        if (!actor.equals(survey.getOwnerActorId()) || !request.sessionId().equals(survey.getSessionId())
                || !request.originInvoiceId().equals(survey.getOriginInvoiceId())) {
            throw TasteSurveyErrors.notFound();
        }
        return survey;
    }

    private TasteSurveyDto.Response replayLink(TasteSurvey survey, UUID customerId, UUID actor) {
        if (survey.getBindingVersion() == 1 && customerId.equals(survey.getCustomerId())) {
            return response(survey, actor);
        }
        throw TasteSurveyErrors.conflict("TASTE_SURVEY_LINK_CONFLICT",
                "Chỉ có thể liên kết khảo sát khách lẻ một lần");
    }

    private TasteSurveyDto.Response replayCreate(TasteSurvey survey, String hash, UUID actor) {
        if (!hash.equals(survey.getCreateRequestHash())) {
            throw TasteSurveyErrors.conflict("TASTE_SURVEY_IDEMPOTENCY_CONFLICT",
                    "Mã yêu cầu đã được dùng cho nội dung khác");
        }
        return response(survey, actor);
    }

    private TasteSurveyDto.Response replaySubmit(TasteSurvey survey, UUID key, UUID actor) {
        requireSameSubmit(survey, key, submitHash(survey, survey.getCriteriaSha256()));
        return response(survey, actor);
    }

    private void requireSameSubmit(TasteSurvey survey, UUID key, String hash) {
        if (!key.equals(survey.getSubmitRequestKey())) {
            throw immutable();
        }
        if (!hash.equals(survey.getSubmitRequestHash())) {
            throw TasteSurveyErrors.conflict("TASTE_SURVEY_IDEMPOTENCY_CONFLICT",
                    "Mã chốt khảo sát đã được dùng cho nội dung khác");
        }
    }

    private String createHash(Draft draft) {
        ObjectNode input = json.object();
        input.put("customerId", Objects.toString(draft.customerId(), null));
        input.put("originInvoiceId", draft.invoiceId().toString());
        input.put("copiedFromSurveyId", Objects.toString(draft.copiedFrom(), null));
        input.put("taxonomyVersion", draft.taxonomyVersion());
        input.put("taxonomySha256", draft.taxonomySha256());
        input.put("rawDescription", draft.rawDescription());
        input.set("criteria", draft.selection().document());
        return json.sha256(input);
    }

    private String submitHash(TasteSurvey survey, String criteriaHash) {
        return json.sha256(json.object().put("surveyId", survey.getId().toString())
                .put("criteriaSha256", criteriaHash).put("rawDescription", survey.getRawDescription()));
    }

    private void validateTaxonomy(String version, String sha256, TasteCriteria.Selection selection) {
        criteria.validate(selection, taxonomy.requirePublishedRelease(version, sha256));
    }

    private TasteSurvey get(UUID id) {
        return surveys.findById(id).orElseThrow(TasteSurveyErrors::notFound);
    }

    private TasteSurvey ownedSurvey(UUID id, UUID actor, UUID session) {
        TasteSurvey survey = get(id);
        requireOwner(survey, actor, session);
        return survey;
    }

    private TasteSurvey mutationSurvey(UUID id, UUID actor, UUID session) {
        TasteSurvey survey = ownedSurvey(id, actor, session);
        requireUsable(survey);
        return survey;
    }

    private void requireOwner(TasteSurvey survey, UUID actor, UUID session) {
        if (!actor.equals(survey.getOwnerActorId())
                || (survey.getCustomerId() == null && !survey.getSessionId().equals(session))) {
            throw TasteSurveyErrors.notFound();
        }
    }

    private void requireUsable(TasteSurvey survey) {
        if (survey.getStatus() == TasteSurveyStatus.EXPIRED
                || (survey.getCustomerId() == null && !now().isBefore(survey.getExpiresAt()))) {
            throw TasteSurveyErrors.expired();
        }
    }

    private void requireDraft(TasteSurvey survey, long version) {
        if (survey.getStatus() != TasteSurveyStatus.DRAFT) {
            throw immutable();
        }
        requireVersion(survey, version);
    }

    private void requireVersion(TasteSurvey survey, long version) {
        if (survey.getRowVersion() != version) {
            throw TasteSurveyErrors.stale();
        }
    }

    private CustomerServiceException immutable() {
        return TasteSurveyErrors.conflict("TASTE_SURVEY_IMMUTABLE", "Khảo sát đã chốt không thể sửa nội dung");
    }

    private void requireExistingCustomer(UUID id) {
        customers.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> CustomerServiceException.notFound("Không tìm thấy khách hàng"));
    }

    private void requireActiveCustomer(UUID id) {
        Customer customer = customers.findByIdForUpdate(id)
                .orElseThrow(() -> CustomerServiceException.notFound("Không tìm thấy khách hàng"));
        if (customer.getStatus() != CustomerStatus.ACTIVE) {
            throw TasteSurveyErrors.conflict("TASTE_SURVEY_CUSTOMER_INACTIVE", "Khách hàng chưa hoạt động");
        }
    }

    private UUID posActor() {
        if (!SecurityUtils.hasAnyRole(Role.SALE_STAFF, Role.MANAGER, Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Chỉ nhân viên POS được truy cập khảo sát khẩu vị");
        }
        String actor = SecurityUtils.currentUserId();
        try {
            UUID id = UUID.fromString(actor);
            if (!id.toString().equalsIgnoreCase(actor)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return id;
        } catch (IllegalArgumentException failure) {
            throw new AccessDeniedException("Không xác định được nhân viên POS");
        }
    }

    private void touch(TasteSurvey survey, UUID actor, LocalDateTime now) {
        survey.setUpdatedBy(actor);
        survey.setUpdatedAt(now);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC()).truncatedTo(ChronoUnit.MICROS);
    }

    private TasteSurveyDto.Response response(TasteSurvey survey, UUID actor) {
        boolean owner = actor.equals(survey.getOwnerActorId());
        return new TasteSurveyDto.Response(survey.getId(), survey.getCustomerId(),
                owner ? survey.getSessionId() : null, owner ? survey.getOriginInvoiceId() : null,
                survey.getCopiedFromSurveyId(), survey.getStatus(), survey.getRowVersion(), survey.getBindingVersion(),
                survey.getTaxonomyVersion(), survey.getTaxonomySha256(), survey.getCriteriaSha256(),
                survey.getRawDescription(), survey.getCriteria(), utc(survey.getCreatedAt()), utc(survey.getUpdatedAt()),
                utc(survey.getSubmittedAt()), utc(survey.getExpiresAt()), utc(survey.getLinkedAt()));
    }

    private Instant utc(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }

    private record Draft(UUID customerId, UUID invoiceId, UUID copiedFrom, String taxonomyVersion,
                         String taxonomySha256, String rawDescription, TasteCriteria.Selection selection) {
    }
}
