package gov.cms.madie.user.controllers;

import gov.cms.madie.user.exceptions.InvalidHarpIdException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RequiredArgsConstructor
@ControllerAdvice
public class ErrorHandlingControllerAdvice {

  private final ErrorAttributes errorAttributes;

  private Map<String, Object> getErrorAttributes(WebRequest request, HttpStatus httpStatus) {
    ErrorAttributeOptions errorOptions =
        ErrorAttributeOptions.of(ErrorAttributeOptions.Include.MESSAGE);
    Map<String, Object> errorAttributes =
        this.errorAttributes.getErrorAttributes(request, errorOptions);
    errorAttributes.put("status", httpStatus.value());
    errorAttributes.put("error", httpStatus.getReasonPhrase());
    return errorAttributes;
  }

  @ExceptionHandler(InvalidHarpIdException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ResponseBody
  Map<String, Object> onInvalidKeyException(WebRequest request, InvalidHarpIdException ex) {
    return getErrorAttributes(request, HttpStatus.BAD_REQUEST);
  }
}
