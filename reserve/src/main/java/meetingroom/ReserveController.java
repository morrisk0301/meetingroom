package meetingroom;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import java.util.Optional;

@RestController
 public class ReserveController {
  @Autowired
  ReserveRepository reserveRepository;

  @RequestMapping(value="/reserves/check")
  public String userCheck(@RequestBody Reserve reserve){
   Optional<Reserve> result=reserveRepository.findById(reserve.getId());
   if(result.isPresent()){
    if(result.get().getUserId().equals(reserve.getUserId())){

     result.get().setStatus("Started");
     reserveRepository.save(result.get());

     return "valid";
    }
    return "invalid";
   }
   else return "invalid";
  }

 }
