package com.courier.modules.finance.application.command;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param branchId whose wallet — required for an admin raising a request on a branch's
 *                 behalf; a branch caller's own branch is used regardless of what (if
 *                 anything) is passed here
 * @param amount        strictly positive, the amount being asked for — not credited yet
 * @param remarks       free text: why the branch needs it
 * @param proofImageUrl mandatory — URL of a proof image, from {@code uploadProofImage()}
 */
public record CreateTopupRequestCommand(UUID branchId, BigDecimal amount, String remarks, String proofImageUrl) {
}
