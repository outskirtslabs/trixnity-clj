package ol.trixnity.bridge

import clojure.lang.IPersistentMap
import de.connect2x.trixnity.client.RepositoriesModule
import de.connect2x.trixnity.client.store.repository.*
import de.connect2x.trixnity.core.serialization.events.EventContentSerializerMappings
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.nio.file.Path

fun RepositoriesModule.Companion.sqlite4clj(databasePath: Path): RepositoriesModule = RepositoriesModule {
    module {
        single {
            Sqlite4cljClojure.openHandle(databasePath.toAbsolutePath().toString(), get<Json>()).also {
                Sqlite4cljClojure.ensureSchema(it)
            }
        }
        single<RepositoryTransactionManager> { Sqlite4cljRepositoryTransactionManager(get()) }
        single<IPersistentMap> {
            Sqlite4cljClojure.createRepositories(
                get(),
                get<EventContentSerializerMappings>(),
            )
        }
        single<AccountRepository> { Sqlite4cljClojure.repository(get(), "account") }
        single<AuthenticationRepository> { Sqlite4cljClojure.repository(get(), "authentication") }
        single<ServerDataRepository> { Sqlite4cljClojure.repository(get(), "server-data") }
        single<OutdatedKeysRepository> { Sqlite4cljClojure.repository(get(), "outdated-keys") }
        single<DeviceKeysRepository> { Sqlite4cljClojure.repository(get(), "device-keys") }
        single<CrossSigningKeysRepository> { Sqlite4cljClojure.repository(get(), "cross-signing-keys") }
        single<KeyVerificationStateRepository> { Sqlite4cljClojure.repository(get(), "key-verification-state") }
        single<KeyChainLinkRepository> { Sqlite4cljKeyChainLinkRepository(Sqlite4cljClojure.repository(get(), "key-chain-link")) }
        single<SecretsRepository> { Sqlite4cljClojure.repository(get(), "secrets") }
        single<SecretKeyRequestRepository> { Sqlite4cljClojure.repository(get(), "secret-key-request") }
        single<RoomKeyRequestRepository> { Sqlite4cljClojure.repository(get(), "room-key-request") }
        single<OlmAccountRepository> { Sqlite4cljClojure.repository(get(), "olm-account") }
        single<OlmForgetFallbackKeyAfterRepository> { Sqlite4cljClojure.repository(get(), "olm-forget-fallback-key-after") }
        single<OlmSessionRepository> { Sqlite4cljClojure.repository(get(), "olm-session") }
        single<InboundMegolmSessionRepository> { Sqlite4cljClojure.repository(get(), "inbound-megolm-session") }
        single<InboundMegolmMessageIndexRepository> { Sqlite4cljClojure.repository(get(), "inbound-megolm-message-index") }
        single<OutboundMegolmSessionRepository> { Sqlite4cljClojure.repository(get(), "outbound-megolm-session") }
        single<RoomRepository> { Sqlite4cljClojure.repository(get(), "room") }
        single<RoomUserRepository> { Sqlite4cljRoomUserRepository(Sqlite4cljClojure.repository(get(), "room-user")) }
        single<RoomUserReceiptsRepository> { Sqlite4cljRoomUserReceiptsRepository(Sqlite4cljClojure.repository(get(), "room-user-receipts")) }
        single<RoomStateRepository> { Sqlite4cljRoomStateRepository(Sqlite4cljClojure.repository(get(), "room-state")) }
        single<TimelineEventRepository> { Sqlite4cljTimelineEventRepository(Sqlite4cljClojure.repository(get(), "timeline-event")) }
        single<TimelineEventRelationRepository> { Sqlite4cljTimelineEventRelationRepository(Sqlite4cljClojure.repository(get(), "timeline-event-relation")) }
        single<RoomOutboxMessageRepository> { Sqlite4cljRoomOutboxMessageRepository(Sqlite4cljClojure.repository(get(), "room-outbox-message")) }
        single<MediaCacheMappingRepository> { Sqlite4cljClojure.repository(get(), "media-cache-mapping") }
        single<GlobalAccountDataRepository> { Sqlite4cljClojure.repository(get(), "global-account-data") }
        single<RoomAccountDataRepository> { Sqlite4cljRoomAccountDataRepository(Sqlite4cljClojure.repository(get(), "room-account-data")) }
        single<UserPresenceRepository> { Sqlite4cljClojure.repository(get(), "user-presence") }
        single<NotificationRepository> { Sqlite4cljNotificationRepository(Sqlite4cljClojure.repository(get(), "notification")) }
        single<NotificationStateRepository> { Sqlite4cljClojure.repository(get(), "notification-state") }
        single<NotificationUpdateRepository> { Sqlite4cljNotificationUpdateRepository(Sqlite4cljClojure.repository(get(), "notification-update")) }
        single<MigrationRepository> { Sqlite4cljClojure.repository(get(), "migration") }
    }
}
