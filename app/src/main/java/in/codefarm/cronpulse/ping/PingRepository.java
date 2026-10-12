package in.codefarm.cronpulse.ping;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PingRepository extends JpaRepository<Ping, Long> {

    /** The ping log, newest first. Served by the (check_id, received_at DESC) index. */
    Page<Ping> findByCheckIdOrderByReceivedAtDesc(Long checkId, Pageable pageable);
}
