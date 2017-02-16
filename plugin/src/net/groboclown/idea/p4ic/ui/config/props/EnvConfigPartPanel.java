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

package net.groboclown.idea.p4ic.ui.config.props;

import com.intellij.openapi.project.Project;
import net.groboclown.idea.p4ic.P4Bundle;
import net.groboclown.idea.p4ic.config.P4ProjectConfig;
import net.groboclown.idea.p4ic.config.part.EnvCompositePart;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ResourceBundle;

public class EnvConfigPartPanel
        extends ConfigPartPanel<EnvCompositePart> {
    private JPanel rootPanel;
    private JTextPane descriptionLabel;

    EnvConfigPartPanel(@NotNull Project project, @NotNull EnvCompositePart part) {
        super(project, part);
    }

    @Override
    @NotNull
    EnvCompositePart copyPart() {
        return new EnvCompositePart(getProject());
    }

    @Override
    public boolean isModified(@NotNull EnvCompositePart originalPart) {
        // Never modifiable
        return false;
    }

    @Override
    public void updateConfigPartFromUI() {
        // do nothing
    }

    @Nls
    @NotNull
    @Override
    public String getTitle() {
        return P4Bundle.getString("configuration.stack.env.title");
    }

    @NotNull
    @Override
    public JPanel getRootPanel() {
        return rootPanel;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        rootPanel = new JPanel();
        rootPanel.setLayout(new BorderLayout(0, 0));
        descriptionLabel = new JTextPane();
        descriptionLabel.setFont(UIManager.getFont("Label.font"));
        descriptionLabel.setText(
                ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("connection.env.description"));
        rootPanel.add(descriptionLabel, BorderLayout.CENTER);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }
}