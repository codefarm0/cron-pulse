package in.codefarm.cronpulse.alert;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertChannelRepository extends JpaRepository<AlertChannel, Long> {

    List<AlertChannel> findByOrganizationIdOrderByName(Long organizationId);
}
