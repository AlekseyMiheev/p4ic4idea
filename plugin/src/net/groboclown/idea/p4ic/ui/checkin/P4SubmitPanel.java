/* *************************************************************************
 * (c) Copyright 2015 Zilliant Inc. All rights reserved.                   *
 * *************************************************************************
 *                                                                         *
 * THIS MATERIAL IS PROVIDED "AS IS." ZILLIANT INC. DISCLAIMS ALL          *
 * WARRANTIES OF ANY KIND WITH REGARD TO THIS MATERIAL, INCLUDING,         *
 * BUT NOT LIMITED TO ANY IMPLIED WARRANTIES OF NONINFRINGEMENT,           *
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.                   *
 *                                                                         *
 * Zilliant Inc. shall not be liable for errors contained herein           *
 * or for incidental or consequential damages in connection with the       *
 * furnishing, performance, or use of this material.                       *
 *                                                                         *
 * Zilliant Inc. assumes no responsibility for the use or reliability      *
 * of interconnected equipment that is not furnished by Zilliant Inc,      *
 * or the use of Zilliant software with such equipment.                    *
 *                                                                         *
 * This document or software contains trade secrets of Zilliant Inc. as    *
 * well as proprietary information which is protected by copyright.        *
 * All rights are reserved.  No part of this document or software may be   *
 * photocopied, reproduced, modified or translated to another language     *
 * prior written consent of Zilliant Inc.                                  *
 *                                                                         *
 * ANY USE OF THIS SOFTWARE IS SUBJECT TO THE TERMS AND CONDITIONS         *
 * OF A SEPARATE LICENSE AGREEMENT.                                        *
 *                                                                         *
 * The information contained herein has been prepared by Zilliant Inc.     *
 * solely for use by Zilliant Inc., its employees, agents and customers.   *
 * Dissemination of the information and/or concepts contained herein to    *
 * other parties is prohibited without the prior written consent of        *
 * Zilliant Inc..                                                          *
 *                                                                         *
 * (c) Copyright 2015 Zilliant Inc. All rights reserved.                   *
 *                                                                         *
 * *************************************************************************/

package net.groboclown.idea.p4ic.ui.checkin;

import com.intellij.icons.AllIcons.Actions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.table.JBTable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import net.groboclown.idea.p4ic.P4Bundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;

public class P4SubmitPanel {
    private static final Logger LOG = Logger.getInstance(P4SubmitPanel.class);

    private JTable myJobTable;
    private JButton myAddJobButton;
    private JButton myBrowseButton;
    private JButton myRemoveButton;
    private JTextField myJobIdField;
    private JobTableModel jobTableModel;
    // JDK 1.6 does not have the generic form of the combo box.
    private JComboBox/*<String>*/ myJobStatus;
    private JPanel myRootPanel;
    private JLabel myAssociateJobExpander;
    private JPanel myExpandedPanel;
    private JLabel myJobsDisabledLabel;
    private DefaultComboBoxModel/*<String>*/ jobStatusModel;

    private final SubmitContext context;

    @NotNull
    private Set<String> lastJobList = Collections.emptySet();

    private boolean expandState = false;


    public P4SubmitPanel(final SubmitContext context) {
        this.context = context;


        // UI setup code - compiler will inject the initialization from the
        // form.

        $$$setupUI$$$();
        // Set the visibility of the expanded panel first.
        myExpandedPanel.setVisible(false);

        myJobTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myJobTable.setRowSelectionAllowed(true);
        myJobTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(final ListSelectionEvent e) {
                if (! e.getValueIsAdjusting()) {
                    // Something in this call is disabling
                    // the currently selected item in the table.
                    updateStatus();
                }
            }
        });
        myAddJobButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                String jobId = getJobIdFieldText();
                if (jobId != null) {
                    if (context.addJobId(jobId)) {
                        // job was added successfully
                        myJobIdField.setText("");
                        jobTableModel.fireTableDataChanged();
                    }
                    // TODO else report error that job wasn't found
                }
            }
        });
        myRemoveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                int rowId = myJobTable.getSelectedRow();
                if (rowId >= 0 && rowId < context.getJobIds().size()) {
                    context.removeJobId(
                            context.getJobIds().get(rowId));
                    jobTableModel.fireTableDataChanged();
                }
            }
        });
        myBrowseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                // TODO replace with real action
                Messages.showMessageDialog(context.getProject(),
                        "Browsing for jobs is not yet implemented",
                        "Not implemented",
                        Messages.getErrorIcon());
            }
        });
        myJobIdField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(final DocumentEvent e) {
                updateStatus();
            }

            @Override
            public void removeUpdate(final DocumentEvent e) {
                updateStatus();
            }

            @Override
            public void changedUpdate(final DocumentEvent e) {
                updateStatus();
            }
        });
        myJobStatus.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                final Object selected = myJobStatus.getSelectedItem();
                if (selected != null) {
                    context.setSubmitStatus(selected.toString());
                }
            }
        });
        myAssociateJobExpander.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(final MouseEvent e) {
                expandState = ! expandState;
                updateStatus();
            }
        });
        myAssociateJobExpander.setIcon(Actions.Right);
        myJobsDisabledLabel.setVisible(false);
    }

    @NotNull
    public JPanel getRootPanel() {
        return myRootPanel;
    }


    public void updateStatus() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                boolean needsLayout = false;

                final boolean jobsUpdated = ! (
                        lastJobList.containsAll(context.getJobIds()) &&
                        context.getJobIds().containsAll(lastJobList));

                if (jobsUpdated) {
                    final int jobSelectionIndex = myJobTable.getSelectedRow();
                    final String jobTableSelection;
                    if (jobSelectionIndex >= 0 && jobSelectionIndex < context.getJobIds().size()) {
                        jobTableSelection = context.getJobIds().get(jobSelectionIndex);
                    } else {
                        jobTableSelection = null;
                    }
                    jobTableModel.fireTableDataChanged();
                    if (jobTableSelection != null) {
                        int rowId = context.getJobIds().indexOf(jobTableSelection);
                        if (rowId >= 0 && rowId != jobSelectionIndex) {
                            myJobTable.setRowSelectionInterval(rowId, rowId);
                        }
                    }
                    lastJobList = new HashSet<String>(context.getJobIds());
                }

                if (context.isJobAssociationValid()) {
                    myJobTable.setEnabled(true);
                    myAddJobButton.setEnabled(getJobIdFieldText() != null);
                    myRemoveButton.setEnabled(myJobTable.getSelectedRow() >= 0);

                    // TODO change to "true" when implemented
                    myBrowseButton.setEnabled(false);
                    myJobStatus.setEnabled(true);
                    final Object selectedJob = myJobStatus.getSelectedItem();
                    jobStatusModel.removeAllElements();
                    boolean foundSelected = false;
                    for (String status : context.getJobStatuses()) {
                        jobStatusModel.addElement(status);
                        if (status.equals(selectedJob)) {
                            foundSelected = true;
                        }
                    }
                    if (foundSelected) {
                        myJobStatus.setSelectedItem(selectedJob);
                    } else {
                        // The P4 default job status.
                        // if "closed" is not in the list, it will simply
                        // be rejected, and no error is thrown.
                        myJobStatus.setSelectedItem("closed");
                    }
                    if (myJobsDisabledLabel.isVisible()) {
                        myJobsDisabledLabel.setVisible(false);
                        needsLayout = true;
                    }
                } else {
                    myJobTable.setEnabled(false);
                    myAddJobButton.setEnabled(false);
                    myRemoveButton.setEnabled(false);
                    myBrowseButton.setEnabled(false);
                    myJobIdField.setEnabled(false);
                    myJobStatus.setEnabled(false);
                    if (! myJobsDisabledLabel.isVisible()) {
                        myJobsDisabledLabel.setVisible(true);
                        needsLayout = true;
                    }
                }

                if (expandState) {
                    if (! Actions.Down.equals(myAssociateJobExpander.getIcon())) {
                        myAssociateJobExpander.setIcon(Actions.Down);
                        myExpandedPanel.setVisible(true);
                        needsLayout = true;
                    }
                } else {
                    if (!Actions.Right.equals(myAssociateJobExpander.getIcon())) {
                        myAssociateJobExpander.setIcon(Actions.Right);
                        myExpandedPanel.setVisible(false);
                        needsLayout = true;
                    }
                }

                if (needsLayout) {
                    myRootPanel.doLayout();
                }
            }
        });
    }


    private void createUIComponents() {
        // place custom component creation code here
        jobTableModel = new JobTableModel();
        myJobTable = new JBTable(jobTableModel);

        jobStatusModel = new DefaultComboBoxModel();
        myJobStatus = new ComboBox(jobStatusModel);
    }


    @Nullable
    private String getJobIdFieldText() {
        String text = myJobIdField.getText();
        if (text == null || text.length() <= 0) {
            return null;
        }
        return text;
    }


    private static final String[] COLUMN_NAMES = {
            P4Bundle.getString("submit.job.table.column.name"),
            P4Bundle.getString("submit.job.table.column.assignee"),
            P4Bundle.getString("submit.job.table.column.description"),
    };

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        myRootPanel = new JPanel();
        myRootPanel.setLayout(new GridLayoutManager(4, 4, new Insets(0, 0, 0, 0), -1, -1));
        myRootPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.black), ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("submit.job.title")));
        final JScrollPane scrollPane1 = new JScrollPane();
        myRootPanel.add(scrollPane1, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myJobTable.setCellSelectionEnabled(false);
        myJobTable.setColumnSelectionAllowed(false);
        myJobTable.setFillsViewportHeight(true);
        myJobTable.setShowVerticalLines(false);
        myJobTable.setSurrendersFocusOnKeystroke(true);
        scrollPane1.setViewportView(myJobTable);
        myAddJobButton = new JButton();
        myAddJobButton.setEnabled(false);
        this.$$$loadButtonText$$$(myAddJobButton, ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("submit.job.add.button"));
        myRootPanel.add(myAddJobButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myBrowseButton = new JButton();
        myBrowseButton.setEnabled(false);
        this.$$$loadButtonText$$$(myBrowseButton, ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("submit.job.browse.button"));
        myRootPanel.add(myBrowseButton, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        myRootPanel.add(spacer1, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myJobIdField = new JTextField();
        myRootPanel.add(myJobIdField, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("submit.job.status"));
        myRootPanel.add(label1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("submit.job.id"));
        myRootPanel.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myRootPanel.add(myJobStatus, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myRemoveButton = new JButton();
        myRemoveButton.setEnabled(false);
        this.$$$loadButtonText$$$(myRemoveButton, ResourceBundle.getBundle("net/groboclown/idea/p4ic/P4Bundle").getString("submit.job.remove.button"));
        myRootPanel.add(myRemoveButton, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label1.setLabelFor(myJobStatus);
        label2.setLabelFor(myJobIdField);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return myRootPanel;
    }


    private class JobTableModel extends AbstractTableModel {
        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public Object getValueAt(final int rowIndex, final int columnIndex) {
            // TODO replace with getting real values, not just strings
            if (columnIndex == 0) {
                return context.getJobIds().get(rowIndex);
            }
            return "";
        }

        @Override
        public int getRowCount() {
            return context.getJobIds().size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }
    }
}
