//package org.hvtra.promotionservice.repository;
//
//import org.hvtra.promotionservice.entity.Event;
//import org.hvtra.promotionservice.entity.Promotion;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.Optional;
//import java.util.UUID;
//
//public interface PromotionRepository extends JpaRepository<Promotion, UUID> {
//
//    Optional<Promotion> findByIdAndIsDeletedFalse(UUID id);
//
//    Page<Promotion> findAllByIsDeletedFalse(Pageable pageable);
//
//    Page<Promotion> findByEventAndIsDeletedFalse(Event event, Pageable pageable);
//}
