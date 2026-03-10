package phase1

import net.folivo.trixnity.client.MatrixClient
import net.folivo.trixnity.clientserverapi.client.MatrixClientServerApiClient
import net.folivo.trixnity.clientserverapi.client.UIA
import net.folivo.trixnity.clientserverapi.model.authentication.AccountType
import net.folivo.trixnity.clientserverapi.model.authentication.Register
import net.folivo.trixnity.clientserverapi.model.uia.AuthenticationRequest

suspend fun MatrixClientServerApiClient.register(
    username: String? = null,
    password: String,
): Result<MatrixClient.LoginInfo> {
    val registerStep = authentication.register(
        password = password,
        username = username,
        accountType = AccountType.USER,
        refreshToken = true,
    ).getOrThrow()
    require(registerStep is UIA.Step<Register.Response>)
    val registerResult = registerStep.authenticate(AuthenticationRequest.Dummy).getOrThrow()
    require(registerResult is UIA.Success<Register.Response>)
    val registerResultValue = registerResult.value
    val userId = registerResultValue.userId
    val deviceId = registerResultValue.deviceId
    val accessToken = registerResultValue.accessToken
    val refreshToken = registerResultValue.refreshToken
    requireNotNull(deviceId)
    requireNotNull(accessToken)
    return Result.success(MatrixClient.LoginInfo(userId, deviceId, accessToken, refreshToken))
}
