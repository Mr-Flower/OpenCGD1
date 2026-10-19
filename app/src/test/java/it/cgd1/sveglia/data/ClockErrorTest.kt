package it.cgd1.sveglia.data

import it.cgd1.sveglia.protocol.Cgd1AuthException
import it.cgd1.sveglia.protocol.Cgd1CommandException
import it.cgd1.sveglia.protocol.Cgd1ConnectionException
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockErrorTest {

    @Test fun permissionWrappedInAConnectionErrorIsStillAPermissionError() =
        assertTrue(Cgd1ConnectionException("connect failed", SecurityException("no BLUETOOTH_CONNECT")).toClockError() is ClockError.PermissionMissing)

    @Test fun rejectedTokenIsAuthRejected() =
        assertTrue(Cgd1AuthException("rejected", Cgd1ConnectionException("link lost")).toClockError() is ClockError.AuthRejected)

    @Test fun commandErrorsStayCommandErrors() =
        assertTrue(Cgd1CommandException("status 0x01").toClockError() is ClockError.Command)

    @Test fun anythingElseIsAConnectionError() =
        assertTrue(IllegalStateException("boom").toClockError() is ClockError.Connection)
}
