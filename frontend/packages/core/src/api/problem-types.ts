const problemTypeBase =
    "https://github.com/luckygc/archive-management/blob/main/docs/api-problems.md#";

export const PROBLEM_TYPES = {
    TOTP_CHALLENGE_INVALID: `${problemTypeBase}totp-challenge-invalid`,
    TOTP_CODE_INVALID: `${problemTypeBase}totp-code-invalid`,
    TOTP_ENROLLMENT_INVALID: `${problemTypeBase}totp-enrollment-invalid`,
    TOTP_ALREADY_ENABLED: `${problemTypeBase}totp-already-enabled`,
} as const;
