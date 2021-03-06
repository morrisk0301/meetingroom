
package meetingroom.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import java.util.Date;

@FeignClient(name="reserve", url="http://localhost:8082")
public interface ReserveService {

    @RequestMapping(method= RequestMethod.GET, path="/reserves/check")
    public String userCheck(@RequestBody Reserve reserve);

}