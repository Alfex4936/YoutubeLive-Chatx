package csw.youtube.chat.profanity.repository;

import csw.youtube.chat.profanity.entity.ProfanityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProfanityLogRepository extends JpaRepository<ProfanityLog, Long> {
}