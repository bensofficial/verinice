/*******************************************************************************
 * Copyright (c) 2011 Daniel Murygin.
 *
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,    
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. 
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Daniel Murygin <dm[at]sernet[dot]de> - initial API and implementation
 ******************************************************************************/
package sernet.verinice.rcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import sernet.gs.ui.rcp.main.ImageCache;
import sernet.gs.ui.rcp.main.RedrawingTableViewer;
import sernet.gs.ui.rcp.main.service.ServiceFactory;
import sernet.hui.common.VeriniceContext;
import sernet.verinice.interfaces.ApplicationRoles;
import sernet.verinice.interfaces.CommandException;
import sernet.verinice.interfaces.IAuthService;
import sernet.verinice.interfaces.ICommandService;
import sernet.verinice.interfaces.IRightsServiceClient;
import sernet.verinice.iso27k.rcp.ComboModel;
import sernet.verinice.iso27k.rcp.IComboModelLabelProvider;
import sernet.verinice.model.auth.Action;
import sernet.verinice.model.auth.Auth;
import sernet.verinice.model.auth.ConfigurationType;
import sernet.verinice.model.auth.OriginType;
import sernet.verinice.model.auth.Profile;
import sernet.verinice.model.auth.ProfileRef;
import sernet.verinice.model.auth.Userprofile;
import sernet.verinice.model.common.configuration.Configuration;
import sernet.verinice.service.account.AccountLoader;
import sernet.verinice.service.commands.LoadVisibleAccounts;

/**
 * @author Daniel Murygin <dm[at]sernet[dot]de>
 */
public class UserprofileDialog extends TitleAreaDialog {

    private static final Logger LOG = Logger.getLogger(UserprofileDialog.class);

    private Combo comboLogin;
    private ComboModel<String> comboModel;

    private TableViewer tableSelected;
    private TableViewer table;
    private TableViewer tableAction;
    private ProfileLabelProvider profileLabelProvider = new ProfileLabelProvider();
    private ProfileTableComparator profileTableComparator = new ProfileTableComparator();

    private Button addAllButton;
    private Button removeAllButton;

    private Auth auth;
    private Userprofile userprofile;
    private List<ProfileRef> selectedProfiles = new ArrayList<>();
    private List<ProfileRef> unselectedProfiles;
    private List<Profile> allProfiles;

    private ProfileRef selectedProfileRef;

    private IRightsServiceClient rightsService;
    private ICommandService commandService;

    public UserprofileDialog(Shell parent) {
        super(parent);
        auth = getRightService().getConfiguration();
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
        allProfiles = auth.getProfiles().getProfile();
        unselectedProfiles = new ArrayList<>(allProfiles.size());
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(Messages.UserprofileDialog_0);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        final int fourColCompCharHeight = 20;
        final int fourColCompNumColumns = 4;
        final int leftCompCharHeight = 40;
        final int rightCompCharHeight = leftCompCharHeight;
        final int rightButtonCompositeWidthHint = rightCompCharHeight;

        setTitle(Messages.UserprofileDialog_1);
        String message = Messages.UserprofileDialog_2;
        if (auth.getType().equals(ConfigurationType.BLACKLIST)) {
            message = message + Messages.UserprofileDialog_19;
        }
        if (auth.getType().equals(ConfigurationType.WHITELIST)) {
            message = message + Messages.UserprofileDialog_20;
        }
        setMessage(message);
        setTitleImage(ImageCache.getInstance().getImage(ImageCache.USERPROFILE_64));
        initializeDialogUnits(parent);

        Composite composite = new Composite(parent, SWT.FILL);
        composite.setLayout(new GridLayout());
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        Composite comboComposite = new Composite(composite, SWT.NONE);
        GridData gridData = new GridData(SWT.FILL, SWT.NONE, true, false);
        comboComposite.setLayoutData(gridData);
        GridLayout gridLayout = new GridLayout(2, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        comboComposite.setLayout(gridLayout);

        Label label = new Label(comboComposite, SWT.WRAP);
        label.setText(Messages.UserprofileDialog_3);

        comboLogin = new Combo(comboComposite, SWT.DROP_DOWN | SWT.READ_ONLY);
        comboLogin.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                comboModel.setSelectedIndex(comboLogin.getSelectionIndex());
                loadProfiles();
            }

        });
        comboModel = new ComboModel<>(new IComboModelLabelProvider() {
            @Override
            public String getLabel(Object label) {
                return (String) label;
            }
        });

        Composite fourColumnComposite = new Composite(composite, SWT.NONE);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        gridData.heightHint = convertHeightInCharsToPixels(fourColCompCharHeight);
        fourColumnComposite.setLayoutData(gridData);
        gridLayout = new GridLayout(fourColCompNumColumns, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        fourColumnComposite.setLayout(gridLayout);

        Composite leftComposite = new Composite(fourColumnComposite, SWT.NONE);
        gridData = new GridData(SWT.FILL, SWT.FILL, true, true);
        gridData.widthHint = convertWidthInCharsToPixels(leftCompCharHeight);
        leftComposite.setLayoutData(gridData);
        gridLayout = new GridLayout(1, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        leftComposite.setLayout(gridLayout);

        Composite centerComposite = new Composite(fourColumnComposite, SWT.NONE);
        gridLayout = new GridLayout(1, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        centerComposite.setLayout(gridLayout);
        centerComposite.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

        Composite rightComposite = new Composite(fourColumnComposite, SWT.NONE);
        gridData = new GridData(SWT.FILL, SWT.FILL, false, true);
        gridData.widthHint = convertWidthInCharsToPixels(rightCompCharHeight);
        rightComposite.setLayoutData(gridData);
        gridLayout = new GridLayout(1, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        rightComposite.setLayout(gridLayout);

        Composite rightButtonComposite = new Composite(fourColumnComposite, SWT.NONE);
        gridData = new GridData(SWT.CENTER, SWT.FILL, false, true);
        gridData.widthHint = convertWidthInCharsToPixels(rightButtonCompositeWidthHint);
        rightButtonComposite.setLayoutData(gridData);
        gridLayout = new GridLayout(1, false);
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        rightButtonComposite.setLayout(gridLayout);

        tableSelected = createTable(leftComposite, Messages.UserprofileDialog_4);
        tableSelected.setLabelProvider(profileLabelProvider);
        tableSelected.setComparator(profileTableComparator);
        tableSelected.setContentProvider(new ArrayContentProvider());
        tableSelected.refresh(true);

        table = createTable(rightComposite, Messages.UserprofileDialog_5);
        table.setLabelProvider(profileLabelProvider);
        table.setComparator(profileTableComparator);
        table.setContentProvider(new ArrayContentProvider());
        table.refresh(true);

        createButtons(centerComposite);
        createProfileButtons(rightButtonComposite);

        tableAction = createTable(rightButtonComposite, Messages.UserprofileDialog_6);
        tableAction.setLabelProvider(profileLabelProvider);
        tableAction.setComparator(profileTableComparator);
        tableAction.setContentProvider(new ArrayContentProvider());
        tableAction.refresh(true);

        initializeContent();

        String username = ((IAuthService) VeriniceContext.get(VeriniceContext.AUTH_SERVICE))
                .getUsername();
        loadProfiles(username);
        comboModel.setSelectedObject(username);
        comboLogin.select(comboModel.getSelectedIndex());

        Dialog.applyDialogFont(composite);

        return composite;
    }

    private void initializeContent() {
        tableSelected.setInput(selectedProfiles);

        setUnselected();
        table.setInput(unselectedProfiles);

        Set<String> nameSet = new HashSet<>();
        try {
            nameSet.addAll(loadUserNames());

            boolean isLocalAdmin = getAuthService()
                    .currentUserHasRole(new String[] { ApplicationRoles.ROLE_LOCAL_ADMIN });

            if (isLocalAdmin) {
                nameSet.addAll(AccountLoader.loadGroupNamesForLocalAdmin());
            } else {
                List<String> groupNames = getRightService().getGroupnames();
                for (String groupname : groupNames) {
                    if (groupname != null && !groupname.isEmpty()) {
                        nameSet.add(groupname);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("account groups loading failed", ex);
        }

        comboModel.addAll(nameSet);
        comboModel.sort();
        comboLogin.setItems(comboModel.getLabelArray());
    }

    private Collection<String> loadUserNames() throws CommandException {
        LoadVisibleAccounts command = new LoadVisibleAccounts();
        command = getCommandService().executeCommand(command);
        List<Configuration> accountList = command.getAccountList();
        Set<String> userNames = new HashSet<>();
        for (Configuration account : accountList) {
            userNames.add(account.getUser());
        }
        return userNames;
    }

    private void setUnselected() {
        unselectedProfiles.clear();

        for (Profile profile : allProfiles) {
            if (isLocalAdminCreator(profile)
                    && !containsProfileRef(selectedProfiles, profile.getName())) {
                // create a reference to the profile
                ProfileRef profileRef = new ProfileRef();
                profileRef.setName(profile.getName());
                unselectedProfiles.add(profileRef);
            }
        }
    }

    private boolean isLocalAdminCreator(Profile profile) {
        boolean isLocalAdmin = getAuthService()
                .currentUserHasRole(new String[] { ApplicationRoles.ROLE_LOCAL_ADMIN });
        return !isLocalAdmin || (isLocalAdmin && AccountLoader.isLocalAdminCreator(profile));
    }

    private boolean containsProfileRef(List<ProfileRef> profileRefs, String profileName) {
        boolean contains = false;
        for (ProfileRef profileRef : profileRefs) {
            if (profileRef.getName().equals(profileName)) {
                contains = true;
                break;
            }
        }
        return contains;
    }

    protected void loadProfiles() {
        String selected = comboModel.getSelectedObject();
        loadProfiles(selected);
    }

    private void loadProfiles(String username) {
        allProfiles = auth.getProfiles().getProfile();
        table.remove(unselectedProfiles);
        tableSelected.remove(selectedProfiles);
        boolean profileFound = false;
        for (Userprofile internalUserprofile : auth.getUserprofiles().getUserprofile()) {
            if (username.equals(internalUserprofile.getLogin())) {
                this.userprofile = internalUserprofile;
                selectedProfiles = internalUserprofile.getProfileRef();
                profileFound = true;
                break;
            }
        }
        if (!profileFound) {
            // create a new one
            Userprofile internalUserprofile = new Userprofile();
            internalUserprofile.setLogin(username);
            auth.getUserprofiles().getUserprofile().add(internalUserprofile);
            this.userprofile = internalUserprofile;
            selectedProfiles = internalUserprofile.getProfileRef();
        }
        setUnselected();
        boolean isLocalAdmin = getAuthService()
                .currentUserHasRole(new String[] { ApplicationRoles.ROLE_LOCAL_ADMIN });
        if (isLocalAdmin) {
            List<ProfileRef> selectedLocalAdminProfileRefs = new ArrayList<>(
                    selectedProfiles.size());
            for (ProfileRef profileRef : selectedProfiles) {
                if (AccountLoader.isLocalAdminCreator(getProfil(profileRef))) {
                    selectedLocalAdminProfileRefs.add(profileRef);
                }
            }
            tableSelected.setInput(selectedLocalAdminProfileRefs);
        } else {
            tableSelected.setInput(selectedProfiles);
        }

        table.setInput(unselectedProfiles);
        table.refresh(true);
        tableSelected.refresh(true);
        removeAllButton.setEnabled(!selectedProfiles.isEmpty());
        addAllButton.setEnabled(!unselectedProfiles.isEmpty());
    }

    private Profile getProfil(ProfileRef profileRef) {
        for (Profile profile : allProfiles) {
            if (profile.getName().equals(profileRef.getName())) {
                return profile;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.dialogs.Dialog#okPressed()
     */
    @Override
    protected void okPressed() {
        try {
            getRightService().updateConfiguration(auth);
        } catch (Exception e) {
            final String message = "Error while saving userprofiles.";
            LOG.error(message, e);
            MessageDialog.openError(this.getShell(), "Error", message);
        } finally {
            super.okPressed();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.dialogs.Dialog#cancelPressed()
     */
    @Override
    protected void cancelPressed() {
        getRightService().reload();
        super.cancelPressed();
    }

    private TableViewer createTable(Composite parent, String title) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(title);
        label.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));

        int style = SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL;

        TableViewer internalTable = new RedrawingTableViewer(parent, style | SWT.MULTI);

        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true);
        internalTable.getControl().setLayoutData(gd);

        internalTable.setUseHashlookup(true);

        return internalTable;
    }

    private void createButtons(Composite parent) {
        Label spacer = new Label(parent, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        final Button addButton = new Button(parent, SWT.PUSH);
        addButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        addButton.setText(Messages.UserprofileDialog_7);
        addButton.setEnabled(!table.getSelection().isEmpty());

        addAllButton = new Button(parent, SWT.PUSH);
        addAllButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        addAllButton.setText(Messages.UserprofileDialog_8);
        addAllButton.setEnabled(!unselectedProfiles.isEmpty());

        final Button removeButton = new Button(parent, SWT.PUSH);
        removeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        removeButton.setText(Messages.UserprofileDialog_9);
        removeButton.setEnabled(!table.getSelection().isEmpty());

        removeAllButton = new Button(parent, SWT.PUSH);
        removeAllButton.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));
        removeAllButton.setText(Messages.UserprofileDialog_10);
        removeAllButton.setEnabled(!selectedProfiles.isEmpty());

        table.addSelectionChangedListener((SelectionChangedEvent event) -> {
            IStructuredSelection selection = (IStructuredSelection) table.getSelection();
            List selectionList = selection.toList();
            addButton.setEnabled(!selectionList.isEmpty());
            if (!selectionList.isEmpty()) {
                ProfileRef profileRef = (ProfileRef) selectionList.get(0);
                loadActions(profileRef.getName());
            } else {
                loadActions(null);
            }
        });

        addButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                addSelection();
                removeAllButton.setEnabled(true);
                addAllButton.setEnabled(!table.getSelection().isEmpty());
            }
        });

        table.addDoubleClickListener((DoubleClickEvent event) -> {
            addSelection();
            removeAllButton.setEnabled(true);
            addAllButton.setEnabled(!table.getSelection().isEmpty());
        });

        tableSelected.addSelectionChangedListener((SelectionChangedEvent event) -> {
            IStructuredSelection selection = (IStructuredSelection) tableSelected.getSelection();
            List selectionList = selection.toList();
            removeButton.setEnabled(!selectionList.isEmpty());
            if (!selectionList.isEmpty()) {
                ProfileRef profileRef = (ProfileRef) selectionList.get(0);
                loadActions(profileRef.getName());
            } else {
                loadActions(null);
            }
        });

        removeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                removeSelection();
                addAllButton.setEnabled(true);
                removeAllButton.setEnabled(!selectedProfiles.isEmpty());
            }
        });

        tableSelected.addDoubleClickListener((DoubleClickEvent event) -> {
            removeSelection();
            addAllButton.setEnabled(true);
            removeAllButton.setEnabled(!selectedProfiles.isEmpty());
        });

        addAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                selectedProfiles.addAll(unselectedProfiles);
                unselectedProfiles.clear();
                table.refresh();
                tableSelected.refresh();
                addAllButton.setEnabled(false);
                removeAllButton.setEnabled(true);
                userprofile.setOrigin(null);
            }
        });

        removeAllButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                unselectedProfiles.addAll(selectedProfiles);
                selectedProfiles.clear();
                table.refresh();
                tableSelected.refresh();
                removeAllButton.setEnabled(false);
                addAllButton.setEnabled(true);
                userprofile.setOrigin(null);
            }
        });

    }

    protected void loadActions(String name) {
        if (name != null) {
            for (Profile profile : allProfiles) {
                if (profile.getName().equals(name)) {
                    tableAction.setInput(profile.getAction());
                    break;
                }
            }
        } else {
            tableAction.setInput(Collections.<Action> emptyList());
        }
    }

    private void createProfileButtons(Composite parent) {
        Label spacer = new Label(parent, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

        final Button newButton = new Button(parent, SWT.PUSH);
        newButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        newButton.setText(Messages.UserprofileDialog_11);

        final Button editButton = new Button(parent, SWT.PUSH);
        editButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        editButton.setText(Messages.UserprofileDialog_12);
        editButton.setEnabled(!table.getSelection().isEmpty());

        final Button removeButton = new Button(parent, SWT.PUSH);
        removeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        removeButton.setText(Messages.UserprofileDialog_13);
        removeButton.setEnabled(!table.getSelection().isEmpty());

        table.addSelectionChangedListener((SelectionChangedEvent event) -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            List selectionList = selection.toList();
            if (selectionList != null && selectionList.size() == 1
                    && selectionList.get(0) instanceof ProfileRef) {
                selectedProfileRef = (ProfileRef) selectionList.get(0);
            }
            editButton.setEnabled(selectionList != null && !selectionList.isEmpty());
            removeButton.setEnabled(selectionList != null && !selectionList.isEmpty());
        });

        tableSelected.addSelectionChangedListener((SelectionChangedEvent event) -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            List selectionList = selection.toList();
            if (selectionList != null && selectionList.size() == 1
                    && selectionList.get(0) instanceof ProfileRef) {
                selectedProfileRef = (ProfileRef) selectionList.get(0);
            }
            editButton.setEnabled(selectionList != null && !selectionList.isEmpty());
            removeButton.setEnabled(selectionList != null && !selectionList.isEmpty());
        });

        newButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                newProfile();
            }
        });

        editButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                editProfile();
            }
        });

        removeButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                deleteProfile();
            }
        });

    }

    protected void editProfile() {
        if (selectedProfileRef != null) {
            final ProfileDialog profiledialog = new ProfileDialog(getShell(), this.auth,
                    selectedProfileRef.getName());
            if (profiledialog.open() == Window.OK) {
                this.auth = profiledialog.getAuth();
                loadProfiles();
                return;
            }
        }
    }

    protected void deleteProfile() {
        if (selectedProfileRef != null) {
            for (Profile p : auth.getProfiles().getProfile()) {
                if (p.getName().equals(selectedProfileRef.getName())
                        && p.getOrigin().equals(OriginType.DEFAULT)) {
                    MessageDialog.openError(this.getShell(), Messages.UserprofileDialog_15,
                            Messages.UserprofileDialog_16);
                    return;
                }
            }

            if (MessageDialog.openConfirm(this.getShell(), Messages.UserprofileDialog_14,
                    NLS.bind(Messages.UserprofileDialog_18, selectedProfileRef.getName()))) {
                Profile profile = new Profile();
                profile.setName(selectedProfileRef.getName());
                auth.getProfiles().getProfile().remove(profile);

                for (Userprofile internalUserprofile : auth.getUserprofiles().getUserprofile()) {
                    internalUserprofile.getProfileRef().remove(selectedProfileRef);
                }
                loadProfiles();
            }
        }
    }

    protected void newProfile() {
        final ProfileDialog profiledialog = new ProfileDialog(getShell(), this.auth, null);
        if (profiledialog.open() == Window.OK) {
            this.auth = profiledialog.getAuth();
            loadProfiles();
            return;
        }
    }

    /**
     * Moves selected elements in the tree into the table
     */
    private void addSelection() {
        IStructuredSelection selection = (IStructuredSelection) table.getSelection();
        List selectionList = selection.toList();
        selectedProfiles.addAll(selectionList);
        unselectedProfiles.removeAll(selectionList);
        Object[] selectedElements = selection.toArray();
        tableSelected.add(selectedElements);
        table.remove(selectedElements);
        tableSelected.setSelection(selection);
        table.getControl().setFocus();
        userprofile.setOrigin(null);
    }

    /**
     * Moves the selected elements in the table into the tree
     */
    private void removeSelection() {
        IStructuredSelection selection = (IStructuredSelection) tableSelected.getSelection();
        List selectionList = selection.toList();
        selectedProfiles.removeAll(selectionList);
        unselectedProfiles.addAll(selectionList);
        Object[] selectedElements = selection.toArray();
        table.add(selectedElements);
        tableSelected.remove(selectedElements);
        table.setSelection(selection);
        tableSelected.getControl().setFocus();
        userprofile.setOrigin(null);
    }

    IRightsServiceClient getRightService() {
        if (rightsService == null) {
            rightsService = (IRightsServiceClient) VeriniceContext
                    .get(VeriniceContext.RIGHTS_SERVICE);
        }
        return rightsService;
    }

    private ICommandService getCommandService() {
        if (commandService == null) {
            commandService = (ICommandService) VeriniceContext.get(VeriniceContext.COMMAND_SERVICE);
        }
        return commandService;
    }

    private IAuthService getAuthService() {
        return ServiceFactory.lookupAuthService();
    }
}
