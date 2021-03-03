package meetingroom.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import java.util.Date;

@FeignClient(name="reserve", url="${api.reserve.url}")
public interface ReserveService {
    @RequestMapping(method= RequestMethod.GET, path="/reserves/check")
    public String reserveCheck(@RequestBody Reserve reserve);
}