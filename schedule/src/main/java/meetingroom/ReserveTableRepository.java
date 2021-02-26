package meetingroom;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReserveTableRepository extends CrudRepository<ReserveTable, Long> {

        ReserveTable findByRoomId(Long roomId);
        ReserveTable findByReserveId(Long reserveId);
        void deleteByRoomId(Long roomId);
        void deleteByReserveId(Long reserveId);
}