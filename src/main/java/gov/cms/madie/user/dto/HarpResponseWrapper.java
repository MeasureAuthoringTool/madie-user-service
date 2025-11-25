package gov.cms.madie.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarpResponseWrapper<T> {
    private HttpStatusCode statusCode;
    private HarpErrorResponse error;
    private HttpStatusCodeException exception;
    private T response;

    public boolean isSuccess() {
        return statusCode != null && statusCode.is2xxSuccessful() && error == null && exception == null;
    }
}
