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

package net.groboclown.p4.server.api.values;

/**
 * @see com.perforce.p4java.core.file.FileAction
 */
public enum P4FileAction {
    ADD,
    ADD_EDIT,
    BRANCH,
    EDIT,
    INTEGRATE,
    DELETE,

    // pseudo-actions for internal use
    REVERTED,
    EDIT_RESOLVED,

    // indicates that the file is open for edit, and the
    // file type is changed.
    REOPEN,

    UNKNOWN,

    /** not marked as modified */
    NONE
}
