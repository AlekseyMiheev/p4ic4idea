/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.groboclown.p4.server.api.cache.messagebus;

import com.intellij.util.messages.Topic;
import net.groboclown.p4.server.api.P4CommandRunner;
import net.groboclown.p4.server.api.P4ServerName;
import net.groboclown.p4.server.api.messagebus.MessageBusClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ServerActionCacheMessage
        extends AbstractCacheMessage<ServerActionCacheMessage.Event> {
    private static final String DISPLAY_NAME = "p4ic4idea:server action pending";
    private static final Topic<TopicListener<Event>> TOPIC = createTopic(DISPLAY_NAME);

    public interface Listener {
        void serverActionUpdate(@NotNull Event event);
    }

    public static void addListener(@NotNull MessageBusClient.ApplicationClient client, @NotNull String cacheId,
            @NotNull Listener listener) {
        abstractAddListener(client, TOPIC, cacheId, listener::serverActionUpdate);
    }

    public static void sendEvent(@NotNull Event e) {
        abstractSendEvent(TOPIC, e);
    }

    public enum ActionState {
        PENDING,
        COMPLETED,
        FAILED
    }

    public static class Event extends AbstractCacheUpdateEvent<Event> {
        private final P4CommandRunner.ServerAction action;
        private final P4CommandRunner.ServerResult result;
        private final ActionState state;
        private final P4CommandRunner.ServerResultException error;

        public Event(@NotNull P4ServerName ref,
                @NotNull P4CommandRunner.ServerAction action) {
            super(ref);
            this.action = action;
            this.result = null;
            this.state = ActionState.PENDING;
            this.error = null;
        }

        public Event(@NotNull P4ServerName ref,
                @NotNull P4CommandRunner.ServerAction action,
                @Nullable P4CommandRunner.ServerResult result) {
            super(ref);
            this.action = action;
            this.result = result;
            this.state = ActionState.COMPLETED;
            this.error = null;
        }

        public Event(@NotNull P4ServerName ref,
                @NotNull P4CommandRunner.ServerAction action,
                P4CommandRunner.ServerResultException error) {
            super(ref);
            this.action = action;
            this.state = ActionState.FAILED;
            this.error = error;
            this.result = null;
        }

        public P4CommandRunner.ServerAction getAction() {
            return action;
        }

        public ActionState getState() {
            return state;
        }

        public P4CommandRunner.ServerResultException getError() {
            return error;
        }
    }
}
