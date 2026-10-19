package com.example.auth

/**
 * Formal verification lifecycle state machine for UERI authentication:
 * - IDLE: Awaiting user action
 * - SENDING: Asynchronous dispatch request in progress on I/O thread
 * - OTP_REQUEST_ACCEPTED: Provider confirmed receipt of dispatch request
 * - VERIFICATION_PENDING: Code has been sent and user must enter the OTP
 * - VERIFIED: Code successfully validated by provider / salted verifier
 * - SEND_FAILED: Dispatch rejected by provider or validation failure
 * - DELIVERY_STATUS_UNKNOWN: Transport accepted request but delivery cannot be confirmed
 */
enum class VerificationFlowState {
    IDLE,
    SENDING,
    OTP_REQUEST_ACCEPTED,
    VERIFICATION_PENDING,
    VERIFIED,
    SEND_FAILED,
    DELIVERY_STATUS_UNKNOWN
}
