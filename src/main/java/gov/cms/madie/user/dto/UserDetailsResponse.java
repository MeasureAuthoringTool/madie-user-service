package gov.cms.madie.user.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserDetailsResponse {
    private String msg;
    private String displaycount;
    private String totalcount;
    private List<UserDetail> userdetails;
    private String errorCode;
}

