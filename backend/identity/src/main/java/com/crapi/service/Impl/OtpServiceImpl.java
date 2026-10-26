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
import com.crapi.enums.EStatus;
import com.crapi.exception.EntityNotFoundException;
import com.crapi.model.CRAPIResponse;
import com.crapi.model.ForgetPassword;
import com.crapi.model.OtpForm;
import com.crapi.repository.OtpRepository;
import com.crapi.repository.UserRepository;
import com.crapi.service.OtpService;
import com.crapi.utils.MailBody;
import com.crapi.utils.OTPGenerator;
import com.crapi.utils.SMTPMailServer;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OtpServiceImpl implements OtpService {
  private static final int MAX_OTP_ATTEMPTS = 5;
private static final int OTP_EXPIRY_MINUTES = 5;
private static final int OTP_LOCK_MINUTES = 10;

  @Autowired OtpRepository otpRepository;

  @Autowired UserRepository userRepository;

  @Autowired PasswordEncoder encoder;

  @Autowired SMTPMailServer smtpMailServer;

  /**
   * Service Method for invalidate OTP
   *
   * @param {Otp} otp
   */
  @Transactional
  @Override
  public boolean invalidateOtp(Otp otp) {
    try {
      otp.setStatus(EStatus.INACTIVE.toString());
      Otp saveOtp = otpRepository.save(otp);
      return true;
    } catch (Exception e) {
      log.error("Failed to invalidate OTP");
      return false;
    }
  }

  /**
   * @param otpForm
   * @return otp object, validate the otp and then reset the password of user
   */
  @Transactional
  @Override
  public CRAPIResponse validateOtp(OtpForm otpForm) {
    return validateOtpWithProtection(otpForm);
  }

  @Transactional
  @Override
  public CRAPIResponse secureValidateOtp(OtpForm otpForm) {
    return validateOtpWithProtection(otpForm);
  }

  private CRAPIResponse validateOtpWithProtection(OtpForm otpForm) {
    User user = userRepository.findByEmail(otpForm.getEmail());

    if (user == null) {
      return new CRAPIResponse("Invalid OTP or email.", 401);
    }

    Otp otp = otpRepository.findByUserForUpdate(user);

    if (otp == null) {
      return new CRAPIResponse("Invalid OTP or email.", 401);
    }

    LocalDateTime now = LocalDateTime.now();

    // Reject attempts during lockout.
    if (otp.getLockedUntil() != null
        && now.isBefore(otp.getLockedUntil())) {

      log.warn("OTP_RATE_LIMITED: userId={}", user.getId());

      return new CRAPIResponse(
          "Too many OTP attempts. Please try again later.", 429);
    }

    // Reject expired or previously used OTP.
    if (!EStatus.ACTIVE.toString().equalsIgnoreCase(otp.getStatus())
        || otp.getExpiresAt() == null
        || !now.isBefore(otp.getExpiresAt())) {

      return new CRAPIResponse("Invalid or expired OTP.", 401);
    }

    // Reset the attempt window only after it expires and lockout is over.
    if (otp.getAttemptWindowStartedAt() != null
        && !now.isBefore(otp.getAttemptWindowStartedAt()
            .plusMinutes(OTP_EXPIRY_MINUTES))
        && (otp.getLockedUntil() == null
            || !now.isBefore(otp.getLockedUntil()))) {

      otp.setCount(0);
      otp.setAttemptWindowStartedAt(null);
      otp.setLastFailedAt(null);
      otpRepository.save(otp);
    }

    if (otp.getCount() >= MAX_OTP_ATTEMPTS) {
      otp.setStatus(EStatus.INACTIVE.toString());
      otpRepository.save(otp);

      return new CRAPIResponse("OTP attempt limit exceeded.", 429);
    }

    // Invalid OTP.
    if (otpForm.getOtp() == null
        || !otp.getOtp().equals(otpForm.getOtp())) {

      // Start a new five-minute attempt window when the previous one expires.
      if (otp.getAttemptWindowStartedAt() == null
          || !now.isBefore(otp.getAttemptWindowStartedAt()
              .plusMinutes(OTP_EXPIRY_MINUTES))) {
        otp.setCount(0);
        otp.setAttemptWindowStartedAt(now);
      }

      int attempts = otp.getCount() + 1;

      otp.setCount(attempts);
      otp.setLastFailedAt(now);

      log.warn(
          "OTP_INVALID: userId={}, failedAttempts={}",
          user.getId(), attempts);

      if (attempts >= MAX_OTP_ATTEMPTS) {
        otp.setLockedUntil(now.plusMinutes(OTP_LOCK_MINUTES));
        otp.setStatus(EStatus.INACTIVE.toString());

        log.warn(
            "OTP_RATE_LIMITED: userId={}, failedAttempts={}",
            user.getId(), attempts);

        otpRepository.save(otp);

        return new CRAPIResponse(
            "Too many OTP attempts. Please try again later.", 429);
      }

      otpRepository.save(otp);
      return new CRAPIResponse("Invalid OTP.", 401);
    }

    // Consume valid OTP exactly once.
    otp.setStatus(EStatus.INACTIVE.toString());
    otpRepository.save(otp);

    user.setPassword(encoder.encode(otpForm.getPassword()));
    user.setPasswordUpdatedAt(java.time.LocalDate.now());
    userRepository.save(user);

    return new CRAPIResponse(UserMessage.OTP_VERIFIED_SUCCESS, 200);
  }


  /**
   * @param forgetPassword contains user email
   * @return its generate the otp and sent email to register email
   */

@Transactional
@Override
public CRAPIResponse generateOtp(ForgetPassword forgetPassword) {
    User user = userRepository.findByEmail(forgetPassword.getEmail());

    if (user == null) {
        return new CRAPIResponse(
            UserMessage.EMAIL_NOT_REGISTERED + forgetPassword.getEmail(),
            404
        );
    }

    LocalDateTime now = LocalDateTime.now();

    // Lock existing OTP row to prevent concurrent updates.
    Otp existingOtp = otpRepository.findByUserForUpdate(user);

    if (existingOtp != null) {
        // Block OTP regeneration while account is locked.
        if (existingOtp.getLockedUntil() != null
                && now.isBefore(existingOtp.getLockedUntil())) {

            log.warn(
                "OTP_RATE_LIMITED: OTP generation blocked for userId={}",
                user.getId()
            );

            return new CRAPIResponse(
                "Too many OTP attempts. Please try again later.",
                429
            );
        }

        // Do not allow requesting a new OTP while the current one is valid.
        if (EStatus.ACTIVE.toString().equalsIgnoreCase(existingOtp.getStatus())
                && existingOtp.getExpiresAt() != null
                && now.isBefore(existingOtp.getExpiresAt())) {

            return new CRAPIResponse(
                "An OTP has already been issued. Please wait until it expires.",
                429
            );
        }
    }

    String otpCode = OTPGenerator.generateRandom(4);

    if (otpCode == null) {
        throw new IllegalStateException("Unable to generate OTP");
    }

    Otp otpEntity = existingOtp != null
        ? existingOtp
        : new Otp(otpCode, user);

    otpEntity.setOtp(otpCode);
    otpEntity.setStatus(EStatus.ACTIVE.toString());
    otpEntity.setExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));

    // Preserve failed attempts within the current five-minute window.
    if (existingOtp == null
            || existingOtp.getAttemptWindowStartedAt() == null
            || !now.isBefore(existingOtp.getAttemptWindowStartedAt()
                .plusMinutes(OTP_EXPIRY_MINUTES))) {
        otpEntity.setCount(0);
        otpEntity.setLastFailedAt(null);
        otpEntity.setAttemptWindowStartedAt(null);
    }

    // Clear an expired lock only.
    if (otpEntity.getLockedUntil() != null
            && !now.isBefore(otpEntity.getLockedUntil())) {
        otpEntity.setLockedUntil(null);
    }

    otpRepository.save(otpEntity);

    smtpMailServer.sendMail(
        user.getEmail(),
        MailBody.otpMailBody(otpEntity),
        "crAPI OTP"
    );

    return new CRAPIResponse(
        UserMessage.OTP_SEND_SUCCESS_ON_EMAIL + user.getEmail(),
        200
    );
}


  /**
   * @param otp
   * @return
   */
  public boolean validateOTPAndEmail(Otp otp, OtpForm otpForm) {
    if (otp != null) {
      if (otp.getStatus().equalsIgnoreCase(EStatus.ACTIVE.toString())
          && otp.getOtp().equalsIgnoreCase(otpForm.getOtp())
          && otp.getUser().getEmail().equalsIgnoreCase(otpForm.getEmail())) {
        return true;
      }
      return false;
    }
    throw new EntityNotFoundException(Otp.class, "OTP Details Not Found", otpForm.getEmail());
  }
}
