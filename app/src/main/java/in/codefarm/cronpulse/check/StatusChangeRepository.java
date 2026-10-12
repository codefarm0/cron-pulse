package in.codefarm.cronpulse.check;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusChangeRepository extends JpaRepository<StatusChange, Long> {

    Page<StatusChange> findByCheckIdOrderByChangedAtDesc(Long checkId, Pageable pageable);
}
