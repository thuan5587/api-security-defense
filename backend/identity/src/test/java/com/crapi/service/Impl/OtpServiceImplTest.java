/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.service.Impl;

import com.crapi.constant.UserMessage;
import com.crapi.entity.Otp;
import com.crapi.entity.User;
import com.crapi.enums.ERole;
import com.crapi.enums.EStatus;
import com.crapi.exception.EntityNotFoundException;
import com.crapi.model.CRAPIResponse;
import com.crapi.model.ForgetPassword;
import com.crapi.model.OtpForm;
import com.crapi.repository.OtpRepository;
import com.crapi.repository.UserRepository;
import com.crapi.utils.SMTPMailServer;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@RunWith(MockitoJUnitRunner.class)
public class OtpServiceImplTest {

  @InjectMocks private OtpServiceImpl otpService;

  @Mock private OtpRepository otpRepository;
  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder encoder;
  @Mock private SMTPMailServer smtpMailServer;

  @Test
  public void invalidateOtpSuccess() {
    Otp otp = getDummyOtp();
    Mockito.when(otpRepository.save(Mockito.any())).thenReturn(otp);
    Assertions.assertTrue(otpService.invalidateOtp(otp));
  }

  @Test
  public void validateOTPAndEmailSuccess() {
    Otp otp = getDummyOtp();
    OtpForm otpForm = getDummyOtpForm();
    otpForm.setOtp(otp.getOtp());
    otp.setStatus(EStatus.ACTIVE.toString());
    otp.getUser().setEmail(otpForm.getEmail());
    Assertions.assertTrue(otpService.validateOTPAndEmail(otp, otpForm));
  }

  @Test(expected = EntityNotFoundException.class)
  public void validateOTPAndEmailThrowsExceptionWhenOtpNull() {
    Otp otp = null;
    OtpForm otpForm = getDummyOtpForm();
    otpService.validateOTPAndEmail(otp, otpForm);
  }

  @Test
  public void validateOTPAndEmailFailureWhenFormAndOtpNotMatch() {
    Otp otp = getDummyOtp();
    OtpForm otpForm = getDummyOtpForm();
    Assertions.assertFalse(otpService.validateOTPAndEmail(otp, otpForm));
  }

  @Test
  public void validateOtpThrowsExceptionWhenUserNotFound() {
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(null);

    CRAPIResponse response = otpService.validateOtp(getDummyOtpForm());

    Assertions.assertEquals(401, response.getStatus());
    Assertions.assertEquals("Invalid OTP or email.", response.getMessage());
  }

  @Test
  public void validateOtpSuccess() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();
    otpForm.setOtp(otp.getOtp());
    otp.setStatus(EStatus.ACTIVE.toString());
    otp.getUser().setEmail(otpForm.getEmail());
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(encoder.encode(Mockito.anyString())).thenReturn("DUMMY ENCODE");
    Mockito.when(otpRepository.findByUserForUpdate(Mockito.any())).thenReturn(otp);
    CRAPIResponse crapiAPIResponse = otpService.validateOtp(otpForm);
    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals(UserMessage.OTP_VERIFIED_SUCCESS, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
  }

  @Test
  public void validateOtpFailure() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(Mockito.any())).thenReturn(otp);
    CRAPIResponse crapiAPIResponse = otpService.validateOtp(otpForm);
    Mockito.verify(userRepository, Mockito.times(0)).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals("Invalid OTP.", crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.UNAUTHORIZED.value(), crapiAPIResponse.getStatus());
  }

  @Test
  public void secureValidateOtpSuccess() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();
    otpForm.setOtp(otp.getOtp());
    otp.setStatus(EStatus.ACTIVE.toString());
    otp.getUser().setEmail(otpForm.getEmail());
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(Mockito.any())).thenReturn(otp);
    CRAPIResponse crapiAPIResponse = otpService.secureValidateOtp(otpForm);
    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals(UserMessage.OTP_VERIFIED_SUCCESS, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
  }

  @Test
  public void secureValidateOtpLocksAfterFifthInvalidAttempt() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();

    otp.setCount(4);
    otp.setAttemptWindowStartedAt(java.time.LocalDateTime.now());

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(Mockito.any())).thenReturn(otp);

    CRAPIResponse response = otpService.secureValidateOtp(otpForm);

    Assertions.assertEquals(429, response.getStatus());
    Assertions.assertEquals(5, otp.getCount());
    Assertions.assertEquals(EStatus.INACTIVE.toString(), otp.getStatus());
    Assertions.assertNotNull(otp.getLockedUntil());
    Assertions.assertTrue(otp.getLockedUntil().isAfter(java.time.LocalDateTime.now()));

    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
  }

  @Test
  public void secureValidateOtpRejectsWhenAttemptLimitAlreadyReached() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();

    otp.setCount(5);
    otp.setAttemptWindowStartedAt(java.time.LocalDateTime.now());

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(Mockito.any())).thenReturn(otp);

    CRAPIResponse response = otpService.secureValidateOtp(otpForm);

    Assertions.assertEquals(429, response.getStatus());
    Assertions.assertEquals(EStatus.INACTIVE.toString(), otp.getStatus());

    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
  }
  @Test
  public void secureValidateOtpFailsWithDefaultCondition() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(Mockito.any())).thenReturn(otp);
    CRAPIResponse crapiAPIResponse = otpService.secureValidateOtp(otpForm);
    Mockito.verify(userRepository, Mockito.times(0)).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals("Invalid OTP.", crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.UNAUTHORIZED.value(), crapiAPIResponse.getStatus());
  }

  @Test
  public void secureValidateOtpThrowsExceptionWhenUserNotFound() {
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(null);

    CRAPIResponse response = otpService.secureValidateOtp(getDummyOtpForm());

    Assertions.assertEquals(401, response.getStatus());
    Assertions.assertEquals("Invalid OTP or email.", response.getMessage());
  }

  @Test
  public void generateOtpFailWhenUserNotFound() {
    ForgetPassword forgetPassword = getDummyForgetPassword();
    String expectedMessage = UserMessage.EMAIL_NOT_REGISTERED + forgetPassword.getEmail();
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(null);
    CRAPIResponse crapiAPIResponse = otpService.generateOtp(getDummyForgetPassword());
    Assertions.assertEquals(expectedMessage, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), crapiAPIResponse.getStatus());
  }

  @Test
  public void generateOtpRejectsWhenActiveOtpAlreadyExists() {
    User user = getDummyUser();
    Otp otp = getDummyOtp();
    String originalCode = otp.getOtp();

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(user)).thenReturn(otp);

    CRAPIResponse response = otpService.generateOtp(getDummyForgetPassword());

    Assertions.assertEquals(429, response.getStatus());
    Assertions.assertEquals(
        "An OTP has already been issued. Please wait until it expires.",
        response.getMessage());
    Assertions.assertEquals(originalCode, otp.getOtp());

    Mockito.verify(otpRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(smtpMailServer, Mockito.never())
        .sendMail(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
  }

  @Test
  public void generateOtpSuccessWhenOtpNotPresentAlreadyPresent() {
    ForgetPassword forgetPassword = getDummyForgetPassword();
    User user = getDummyUser();
    String expectedMessage = UserMessage.OTP_SEND_SUCCESS_ON_EMAIL + user.getEmail();
    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(user)).thenReturn(null);
    Mockito.doNothing()
        .when(smtpMailServer)
        .sendMail(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    CRAPIResponse crapiAPIResponse = otpService.generateOtp(getDummyForgetPassword());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
    Assertions.assertEquals(expectedMessage, crapiAPIResponse.getMessage());
    Assertions.assertEquals(HttpStatus.OK.value(), crapiAPIResponse.getStatus());
  }

  @Test
  public void secureValidateOtpRejectsExpiredOtp() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();

    otp.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
    otpForm.setOtp(otp.getOtp());

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(user)).thenReturn(otp);

    CRAPIResponse response = otpService.secureValidateOtp(otpForm);

    Assertions.assertEquals(401, response.getStatus());
    Assertions.assertEquals("Invalid or expired OTP.", response.getMessage());

    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  public void secureValidateOtpRejectsDuringTenMinuteLock() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();

    otp.setCount(5);
    otp.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
    otpForm.setOtp(otp.getOtp());

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(user)).thenReturn(otp);

    CRAPIResponse response = otpService.secureValidateOtp(otpForm);

    Assertions.assertEquals(429, response.getStatus());
    Assertions.assertEquals(
        "Too many OTP attempts. Please try again later.",
        response.getMessage());

    Mockito.verify(userRepository, Mockito.never()).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.never()).save(Mockito.any());
  }

  @Test
  public void generateOtpSucceedsWhenPreviousOtpExpired() {
    User user = getDummyUser();
    Otp otp = getDummyOtp();

    otp.setExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
    otp.setCount(0);

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(user)).thenReturn(otp);

    CRAPIResponse response = otpService.generateOtp(getDummyForgetPassword());

    Assertions.assertEquals(200, response.getStatus());
    Assertions.assertEquals(
        UserMessage.OTP_SEND_SUCCESS_ON_EMAIL + user.getEmail(),
        response.getMessage());

    Assertions.assertEquals(EStatus.ACTIVE.toString(), otp.getStatus());
    Assertions.assertNotNull(otp.getExpiresAt());
    Assertions.assertTrue(
        otp.getExpiresAt().isAfter(java.time.LocalDateTime.now()));

    Mockito.verify(otpRepository, Mockito.times(1)).save(otp);
    Mockito.verify(smtpMailServer, Mockito.times(1))
        .sendMail(
            Mockito.eq(user.getEmail()),
            Mockito.anyString(),
            Mockito.eq("crAPI OTP"));
  }

  @Test
  public void secureValidateOtpRejectsReuseAfterSuccess() {
    Otp otp = getDummyOtp();
    User user = getDummyUser();
    OtpForm otpForm = getDummyOtpForm();

    otpForm.setOtp(otp.getOtp());

    Mockito.when(userRepository.findByEmail(Mockito.anyString())).thenReturn(user);
    Mockito.when(otpRepository.findByUserForUpdate(user)).thenReturn(otp);
    Mockito.when(encoder.encode(Mockito.anyString())).thenReturn("ENCODED_PASSWORD");

    CRAPIResponse firstResponse = otpService.secureValidateOtp(otpForm);

    Assertions.assertEquals(200, firstResponse.getStatus());
    Assertions.assertEquals(EStatus.INACTIVE.toString(), otp.getStatus());

    CRAPIResponse secondResponse = otpService.secureValidateOtp(otpForm);

    Assertions.assertEquals(401, secondResponse.getStatus());
    Assertions.assertEquals("Invalid or expired OTP.", secondResponse.getMessage());

    Mockito.verify(userRepository, Mockito.times(1)).save(Mockito.any());
    Mockito.verify(otpRepository, Mockito.times(1)).save(Mockito.any());
  }

  private ForgetPassword getDummyForgetPassword() {
    ForgetPassword forgetPassword = new ForgetPassword();
    forgetPassword.setEmail("mail@example.com");
    return forgetPassword;
  }

  private OtpForm getDummyOtpForm() {
    OtpForm otpForm = new OtpForm();
    otpForm.setEmail("sample@example.com");
    otpForm.setOtp("123456");
    otpForm.setPassword("password");
    return otpForm;
  }

  private Otp getDummyOtp() {
    Otp otp = new Otp();
    otp.setCount(1);
    otp.setId(1l);
    otp.setOtp("123123");
    otp.setStatus(EStatus.ACTIVE.toString());
    otp.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(5));
    otp.setUser(getDummyUser());
    return otp;
  }

  private User getDummyUser() {
    User user = new User("email@example.com", "9798789212", "Pass", ERole.ROLE_USER);
    user.setId(1l);
    return user;
  }
}
