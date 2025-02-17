/*******************************************************************************
 * Copyright (c) 2009 Alexander Koderman <ak[at]sernet[dot]de>.
 * This program is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU Lesser General Public License 
 * as published by the Free Software Foundation, either version 3 
 * of the License, or (at your option) any later version.
 *     This program is distributed in the hope that it will be useful,    
 * but WITHOUT ANY WARRANTY; without even the implied warranty 
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
 * See the GNU Lesser General Public License for more details.
 *     You should have received a copy of the GNU Lesser General Public 
 * License along with this program. 
 * If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contributors:
 *     Alexander Koderman <ak[at]sernet[dot]de> - initial API and implementation
 *     Robert Schuster <r.schuster@tarent.de> - abstraction to common base class 
 ******************************************************************************/
package sernet.gs.ui.rcp.main.bsi.views;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IActionBars;

import sernet.gs.ui.rcp.main.Activator;
import sernet.gs.ui.rcp.main.ExceptionUtil;
import sernet.gs.ui.rcp.main.ImageCache;
import sernet.gs.ui.rcp.main.RedrawingTableViewer;
import sernet.gs.ui.rcp.main.bsi.editors.EditorFactory;
import sernet.gs.ui.rcp.main.bsi.filter.MassnahmenSiegelFilter;
import sernet.gs.ui.rcp.main.bsi.filter.MassnahmenUmsetzungFilter;
import sernet.gs.ui.rcp.main.common.model.CnAElementFactory;
import sernet.gs.ui.rcp.main.common.model.CnAElementHome;
import sernet.gs.ui.rcp.main.common.model.DefaultModelLoadListener;
import sernet.gs.ui.rcp.main.common.model.IModelLoadListener;
import sernet.gs.ui.rcp.main.common.model.PlaceHolder;
import sernet.gs.ui.rcp.main.service.ServiceFactory;
import sernet.verinice.model.bpm.TodoViewItem;
import sernet.verinice.model.bsi.BSIModel;
import sernet.verinice.model.bsi.ITVerbund;
import sernet.verinice.model.bsi.MassnahmenUmsetzung;
import sernet.verinice.rcp.RightsEnabledView;
import sernet.verinice.service.commands.crud.LoadCnATreeElementTitles;
import sernet.verinice.service.commands.task.FindMassnahmenForITVerbund;

/**
 * Base class for a view that shows instances of {@link MassnahmenUmsetzung} as
 * {@link TodoViewItem}s.
 * 
 * <p>
 * The class provides all the necessary infrastructure to allow viewing those
 * elements and choosing the IT-Verbund to which they belong.
 * </p>
 * 
 * @author koderman[at]sernet[dot]de
 * @author r.schuster[at]tarent[dot]de
 * @author dm[at]sernet[dot]de
 * 
 */
public abstract class GenericMassnahmenView extends RightsEnabledView
        implements IMassnahmenListView {

    private static final Logger LOG = Logger.getLogger(GenericMassnahmenView.class);

    private int loadBlockNumber = 0;

    private boolean isDateSet = false;

    private List<TodoViewItem> allMassnahmen;

    /**
     * Implementation of {@link ContributionItem} which allows choosing one of
     * the potentially many {@link ITVerbund} instances.
     * 
     * <p>
     * Besides choosing a specific compound the chooser also knows a state where
     * no compound is selected.
     * </p>
     * 
     * <p>
     * The compound chooser triggers actions (i.e. loading of measures) when the
     * user interacted with the combo box that displays the compounds.
     * </p>
     * 
     * <p>
     * On the other hand the chooser needs to be kept updated about changes to
     * the data model. E.g. if a new compound is added or an existing one is
     * deleted. The class provides all means to handle these situations on a
     * practical level.
     * </p>
     * 
     * <p>
     * <em>NOTE:</em> All accesses to methods of this class must be done on the
     * SWT event thread in order to succeed.
     * </p>
     * 
     * @author Robert Schuster <r.schuster@tarent.de>
     * 
     */
    private class MassnahmenCompoundChoser extends ContributionItem {

        private Combo combo;

        private List<ITVerbund> elements;

        /**
         * Called by the RCP framework when the component is initialized.
         * 
         * <p>
         * Creates the combo box and registers necessary listeners.
         * </p>
         * 
         */
        @Override
        public void fill(ToolBar parent, int index) {
            final int toolItemWidth = 200;
            ToolItem ti = new ToolItem(parent, SWT.SEPARATOR, index);

            combo = new Combo(parent, SWT.DROP_DOWN | SWT.READ_ONLY);
            if (elements != null) {
                updateCombo();
                combo.setEnabled(true);
            } else {

                combo.setEnabled(false);
                combo.add(Messages.GenericMassnahmenView_1);
                combo.select(0);
            }

            combo.addSelectionListener(new SelectionListener() {

                @Override
                public void widgetDefaultSelected(SelectionEvent e) {
                    widgetSelected(e);
                }

                @Override
                public void widgetSelected(SelectionEvent e) {
                    int s = combo.getSelectionIndex();
                    // First entry means 'show nothing'
                    if (s == 0) {
                        GenericMassnahmenView.this.resetTable(true);
                    } else {
                        // As long as the loading is in progress, no other
                        // IT-Verbund may be selected.
                        combo.setEnabled(false);
                        GenericMassnahmenView.this.loadBlockNumber = 0;
                        GenericMassnahmenView.this.loadMoreAction.setEnabled(true);
                        GenericMassnahmenView.this.loadMeasures(elements.get(s - 1));
                    }
                }

            });

            ti.setControl(combo);
            ti.setWidth(toolItemWidth);
        }

        @Override
        public boolean isDynamic() {
            return true;
        }

        /**
         * Returns the currently selected IT-Verbund.
         * 
         * <p>
         * If this method is called while no IT-Verbund is selected,
         * <code>null</code> is returned.
         * </p>
         * 
         * @return
         */
        ITVerbund getSelectedCompound() {
            int s = combo.getSelectionIndex();
            if (s == 0) {
                return null;
            } else {
                return elements.get(s - 1);
            }
        }

        /**
         * Selects the given {@link ITVerbund} instance.
         * 
         * <p>
         * The success of this method depends on the {@link ITVerbund} instances
         * currently known to the <code>MassnahmenCompoundChoser</code>.
         * </p>
         * 
         * <p>
         * In case the instance is not known (or the <code>compound</code>
         * argument is <code>null</code>) an element is chosen which means 'no
         * compound chosen'.
         * </p>
         * 
         * @param compound
         */
        void setSelectedCompound(ITVerbund compound) {
            if (compound == null) {
                combo.select(0);
            }

            int count = 0;
            for (ITVerbund c : elements) {
                if (c.equals(compound)) {
                    combo.select(count + 1);

                    return;
                }

                count++;
            }

            combo.select(0);
        }

        /**
         * Returns whether the currently selected IT-Verbund is identical to the
         * given one.
         * 
         * <p>
         * In a bigger context this method is needed to decide whether a
         * specific {@link MassnahmenUmsetzung} belongs to the currently
         * selected IT-Verbund.
         * </p>
         * 
         * @param compound
         * @return
         */
        boolean isSelectedCompound(ITVerbund compound) {
            int s = combo.getSelectionIndex();
            if (s == 0) {
                return false;
            }

            return elements.get(s - 1).equals(compound);
        }

        /**
         * Disables the chooser, i.e. making it impossible for the user to
         * select a different compound.
         * 
         * <p>
         * This is used to prevent the user changing the compounds as long as
         * the application is loading measures.
         * </p>
         * 
         * @param b
         */
        void setEnabled(boolean b) {
            if (combo != null) {
                combo.setEnabled(b);
            }
        }

        /**
         * Sets the available compounds.
         * 
         * <p>
         * This method implicitly clears the combo box.
         * </p>
         * 
         * @param elements
         */
        void setElements(List<ITVerbund> elements) {
            this.elements = elements;
            if (combo != null) {
                updateCombo();
            }
        }

        void updateCombo() {
            combo.removeAll();
            combo.add(Messages.GenericMassnahmenView_2);
            combo.select(0);

            for (ITVerbund c : elements) {
                combo.add(c.getTitle());
            }
        }

        /**
         * Adds a compound to the chooser (making it available for the user to
         * select it).
         * 
         * <p>
         * This method is to be called when a new compound was created in the
         * DB.
         * </p>
         * 
         * @param compound
         */
        void compoundAdded(ITVerbund compound) {
            elements.add(compound);
            combo.add(compound.getTitle());
        }

        /**
         * Removes a compound from the chooser (preventing the user from
         * selecting it).
         * 
         * <p>
         * This method is to be called when a new compound was created in the
         * DB.
         * </p>
         * 
         * <p>
         * When the removed compound is the one that was currently selected,
         * this causes a reset of the table in the view.
         * </p>
         * 
         * @param compound
         */
        void compoundRemoved(ITVerbund compound) {
            int i = combo.getSelectionIndex();
            // Either
            if (i > 0 && elements.get(i - 1).equals(compound)) {
                // The deleted compound is the one whose Massnahmen are
                // currently being shown. Before removing it we select
                // the first (dummy) entry and clear the table.
                combo.select(0);
                GenericMassnahmenView.this.resetTable(true);
            }

            i = 0;
            for (ITVerbund c : elements) {
                if (c.equals(compound)) {
                    combo.remove(i + 1);
                    elements.remove(i);

                    return;
                }

                i++;
            }
        }

        /**
         * Updates a compound when it was modified.
         * 
         * <p>
         * This method is to be called when a compound was changed in the DB.
         * </p>
         * 
         * @param compound
         */
        void compoundChanged(ITVerbund compound) {
            int i = 0;
            for (ITVerbund c : elements) {
                if (c.equals(compound)) {
                    elements.set(i, compound);
                    if (combo.getSelectionIndex() == i + 1) {
                        combo.setItem(i + 1, compound.getTitle());
                        combo.select(i + 1);
                    } else {
                        combo.setItem(i + 1, compound.getTitle());
                    }

                    return;
                }

                i++;
            }
        }

    }

    /**
     * TODO rschuster: This class shares much functionality with
     * MassnahmenUmsetzungContentProvider. It would be better to move it there.
     */
    private IModelLoadListener loadListener = new DefaultModelLoadListener() {

        private ITVerbund lastSelectedCompound;

        /**
         * Called when the model is closed.
         * 
         * <p>
         * This happens on an explicit reload and when the DB connection is
         * closed.
         * </p>
         * 
         * <p>
         * On a reload the currently selected compound is saved for a later
         * reuse.
         * </p>
         * 
         */
        @Override
        public void closed(BSIModel model) {
            Display.getDefault().asyncExec(() -> {
                if (CnAElementHome.getInstance().isOpen()) {
                    // Connection still open -> explicit reload
                    lastSelectedCompound = compoundChoser.getSelectedCompound();
                } else {
                    // Connection closed -> throw away compound information
                    lastSelectedCompound = null;
                }

                compoundChoser.setSelectedCompound(null);
                compoundChoser.setEnabled(false);

                resetTable(false);
            });
        }

        /**
         * This is called when the {@link BSIModel} instance is fully loaded.
         * 
         * <p>
         * In case this happened through an explicit reload the view is
         * initialized with the same compound that was selected before the
         * reload occurred.
         * </p>
         */
        @Override
        public void loaded(final BSIModel model) {
            Display.getDefault().asyncExec(() -> {
                try {
                    loadCompounds(lastSelectedCompound);
                } catch (RuntimeException e) {
                    ExceptionUtil.log(e, Messages.GenericMassnahmenView_3);
                }
            });
        }

        /**
         * Loads the ITVerbund instances and fills the combo box with them.
         * 
         * <p>
         * In case the <code>compound</code> argument is <code>null</code> the
         * combo box will be set to an element which means 'no compound chosen'.
         * A message in the table will inform the user that she has to choose
         * one first.
         * </p>
         * 
         * <p>
         * The above behavior is what is expected when the measure view is
         * <em>first</em> opened (or filled with data).
         * </p>
         * 
         * <p>
         * When an {@link ITVerbund} instance is given then the combo box tries
         * to select this one after loading them. In case the given instance
         * does not exist anymore the first behavior is followed.
         * </p>
         * 
         * <p>
         * The second behavior is appropriate when the view is already in use
         * and then a complete model reload occurs.
         * </p>
         * 
         * @param compound
         */
        private void loadCompounds(final ITVerbund compound) {
            if (!CnAElementHome.getInstance().isOpen()) {
                compoundChoser.setEnabled(false);
                return;
            }

            viewer.setInput(new PlaceHolder(Messages.GenericMassnahmenView_6));

            WorkspaceJob job = new LoadCompounds(Messages.GenericMassnahmenView_7, compound);
            job.setUser(false);
            job.schedule();

        }
    };

    private TableViewer viewer;

    protected TableColumn iconColumn;
    protected TableColumn titleColumn;
    protected TableColumn siegelColumn;
    protected TableColumn dateColumn;
    protected TableColumn zielColumn;
    protected TableColumn bearbeiterColumn;

    private Action loadMoreAction;

    private MassnahmenUmsetzungFilter umsetzungFilter;
    private MassnahmenSiegelFilter siegelFilter;

    private MassnahmenCompoundChoser compoundChoser = new MassnahmenCompoundChoser();

    protected abstract ILabelProvider createLabelProvider();

    protected abstract TableSorter createSorter();

    protected abstract void createPartControlImpl(Composite parent);

    @Override
    public final void createPartControl(Composite parent) {
        super.createPartControl(parent);
        // Customized table viewer implementation that automatically
        // replaces the 'data' item when it is looked up.
        // This makes it possible that when
        // * given a model instance A from the DB
        // * given a model instance B from memory
        // * A.equals(B) holds
        //
        // A is put into memory now, since it is regarded as being
        // more recent.
        viewer = new RedrawingTableViewer(parent,
                SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI | SWT.FULL_SELECTION) {
            @Override
            @SuppressWarnings("unchecked")
            public void update(Object o, String[] props) {
                // Access the list containing the elements directly
                Object input = getInput();
                if (input instanceof List<?>) {
                    List<Object> list = (List<Object>) getInput();

                    // Find the old instance using the new one. Works because
                    // old.equals(new) holds (even if certain properties
                    // differ!).
                    int index = list.indexOf(o);

                    // Find out whether the element really is being regarded.
                    if (index >= 0) {
                        // Replace the object in memory with the one from the
                        // DB.
                        list.set(index, o);

                        // Look up whether there is a widget for the object in
                        // question.
                        Widget w = doFindItem(o);
                        if (w != null) {
                            // Replace the object in the widget as well.
                            w.setData(o);

                            // Provokes a refresh the line that changed.
                            super.update(o, props);
                        } else {
                            // There is no widget for the element in question.
                            // Do a
                            // refresh() so one is created.
                            refresh();
                        }

                    }
                }

            }
        };

        createPartControlImpl(parent);

        createFilters();
        Action filterAction = createFilterAction(umsetzungFilter, siegelFilter);

        createPullDownMenu(filterAction);
        createLoadMoreAction();
        Action toggleDateAction = createToggleDateAction();
        MassnahmenUmsetzungContentProvider contentProvider = new MassnahmenUmsetzungContentProvider(
                this);
        viewer.setContentProvider(contentProvider);
        viewer.setLabelProvider(createLabelProvider());

        CnAElementFactory.getInstance().addLoadListener(loadListener);

        viewer.setComparator(createSorter());
        attachListeners();
        fillLocalToolBar(toggleDateAction, filterAction);

        getSite().setSelectionProvider(viewer);

        dateColumn.pack();
    }

    /**
     * Removes whatever is in the table.
     * 
     * <p>
     * If <code>chooseMessage</code> is <code>true</code> then a placeholder is
     * added which tells the user to choose an IT-Verbund.
     * </p>
     * 
     * <p>
     * If the value is <code>false</code> the list will really be empty.
     * </p>
     */
    void resetTable(boolean chooseMessage) {
        if (chooseMessage) {
            viewer.setInput(new PlaceHolder(Messages.GenericMassnahmenView_5));
        } else {
            viewer.setInput(new ArrayList<Object>());
        }
    }

    /**
     * Provokes that the Massnahmen for the currently selected IT-Verbund are
     * reloaded.
     * 
     * <p>
     * In case that no IT-Verbund is selected the method has no effect.
     * </p>
     */
    @Override
    public final void reloadMeasures() {
        ITVerbund compound = compoundChoser.getSelectedCompound();
        if (compound == null) {
            LOG.warn("No IT-Verbund was selected during reload."); //$NON-NLS-1$
        } else {
            loadMeasures(compound);
        }
    }

    protected abstract String getMeasureLoadPlaceholderLabel();

    protected abstract String getMeasureLoadJobLabel();

    protected abstract String getMeasureLoadTaskLabel();

    protected abstract String getTaskErrorLabel();

    /**
     * Loads the MassnahmenUmsetzung instances for the given IT-Verbund.
     * 
     * <p>
     * This method is called when the user chooses an IT-Verbund and when the
     * content provider decides that all data has to be reloaded.
     * </p>
     * 
     * <p>
     * It is expected that the given ITVerbund instance denotes the one
     * currently selected.
     * </p>
     * 
     * @param itVerbund
     */
    private void loadMeasures(final ITVerbund itVerbund) {
        if (!CnAElementHome.getInstance().isOpen()) {
            compoundChoser.setEnabled(false);
            return;
        }

        viewer.setInput(new PlaceHolder(getMeasureLoadPlaceholderLabel()));
        WorkspaceJob job = new LoadMeasures(getMeasureLoadJobLabel(), itVerbund);

        job.setUser(false);
        job.schedule();

    }

    protected abstract String getSortByProperty();

    protected abstract String getDateProperty();

    protected abstract Action createFilterAction(MassnahmenUmsetzungFilter umsetzungFilter,
            MassnahmenSiegelFilter siegelFilter);

    private void createPullDownMenu(Action filterAction) {
        IMenuManager menuManager = getViewSite().getActionBars().getMenuManager();
        menuManager.add(filterAction);
    }

    private void fillLocalToolBar(Action toggleDateAction, Action filterAction) {
        IActionBars bars = getViewSite().getActionBars();
        IToolBarManager manager = bars.getToolBarManager();
        ActionContributionItem item = new ActionContributionItem(loadMoreAction);
        item.setMode(ActionContributionItem.MODE_FORCE_TEXT);
        manager.add(item);

        ActionContributionItem item2 = new ActionContributionItem(toggleDateAction);
        item2.setMode(ActionContributionItem.MODE_FORCE_TEXT);
        manager.add(item2);

        manager.add(filterAction);
        manager.add(compoundChoser);
    }

    private void createLoadMoreAction() {
        loadMoreAction = new LoadMoreAction(this, Messages.GenericMassnahmenView_11);
    }

    private Action createToggleDateAction() {
        Action toggleDateAction = new Action() {
            @Override
            public void run() {
                isDateSet = !isDateSet;
                this.setChecked(isDateSet);
                loadBlockNumber = 0;
                loadMoreAction.setEnabled(true);
                reloadMeasures();
            }
        };
        toggleDateAction.setChecked(isDateSet);
        toggleDateAction.setText(Messages.GenericMassnahmenView_12);
        toggleDateAction.setImageDescriptor(ImageCache.getInstance()
                .getImageDescriptor(ImageCache.MASSNAHMEN_UMSETZUNG_TEILWEISE));
        return toggleDateAction;
    }

    private void createFilters() {
        umsetzungFilter = new MassnahmenUmsetzungFilter(viewer);
        siegelFilter = new MassnahmenSiegelFilter(viewer);
        umsetzungFilter.setUmsetzungPattern(getUmsetzungPattern());
    }

    protected abstract String[] getUmsetzungPattern();

    @Override
    public final void dispose() {
        CnAElementFactory.getInstance().removeLoadListener(loadListener);
        super.dispose();
    }

    private void attachListeners() {
        viewer.addDoubleClickListener(event -> {
            Object sel = ((IStructuredSelection) event.getSelection()).getFirstElement();
            EditorFactory.getInstance().openEditor(sel);

        });
        viewer.addSelectionChangedListener(event -> {
            Object sel = ((IStructuredSelection) event.getSelection()).getFirstElement();
            if (sel instanceof PlaceHolder) {
                reloadMeasures();
            }
        });
    }

    @Override
    public final void setFocus() {
        viewer.getTable().setFocus();
    }

    @Override
    public final void compoundAdded(final ITVerbund compound) {
        Display.getDefault().asyncExec(() -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("handling added compound: " + compound.getTitle()); //$NON-NLS-1$
            }
            compoundChoser.compoundAdded(compound);
        });
    }

    @Override
    public final void compoundRemoved(final ITVerbund compound) {
        Display.getDefault().asyncExec(() -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("handling removed compound: " + compound.getTitle()); //$NON-NLS-1$
            }
            compoundChoser.compoundRemoved(compound);
        });
    }

    @Override
    public final void compoundChanged(final ITVerbund compound) {
        Display.getDefault().asyncExec(() -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("handling changed compound: " + compound.getTitle()); //$NON-NLS-1$
            }
            compoundChoser.compoundChanged(compound);
        });
    }

    @Override
    public final ITVerbund getCurrentCompound() {
        final ITVerbund[] retval = new ITVerbund[1];
        Display.getDefault().syncExec(() -> retval[0] = compoundChoser.getSelectedCompound());

        return retval[0];
    }

    protected static class SortSelectionAdapter extends SelectionAdapter {
        private GenericMassnahmenView view;
        private TableColumn column;
        private int index;

        public SortSelectionAdapter(GenericMassnahmenView view, TableColumn column, int index) {
            this.view = view;
            this.column = column;
            this.index = index;
        }

        @Override
        public void widgetSelected(SelectionEvent e) {
            view.createSorter().setColumn(index);
            int dir = view.viewer.getTable().getSortDirection();
            if (view.viewer.getTable().getSortColumn() == column) {
                dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
            } else {

                dir = SWT.DOWN;
            }
            view.viewer.getTable().setSortDirection(dir);
            view.viewer.getTable().setSortColumn(column);
            view.viewer.refresh();
        }

    }

    protected static class TableSorter extends ViewerComparator {
        private int propertyIndex;
        private static final int DEFAULT_SORT_COLUMN = 1;
        protected static final int DESCENDING = 1;
        protected static final int ASCENDING = 0;
        private int direction = ASCENDING;

        public TableSorter() {
            this.propertyIndex = DEFAULT_SORT_COLUMN;
            this.direction = ASCENDING;
        }

        public void setColumn(int column) {
            if (column == this.propertyIndex) {
                // Same column as last sort; toggle the direction
                direction = (direction == ASCENDING) ? DESCENDING : ASCENDING;
            } else {
                // New column; do an ascending sort
                this.propertyIndex = column;
                direction = ASCENDING;
            }
        }

        @Override
        public int compare(Viewer viewer, Object o1, Object o2) {
            int rc = 0;
            try {
                TodoViewItem mn1 = (TodoViewItem) o1;
                TodoViewItem mn2 = (TodoViewItem) o2;
                if (o1 == null) {
                    if (o2 != null) {
                        rc = 1;
                    }
                } else if (o2 == null) {
                    rc = -1;
                } else {
                    // e1 and e2 != null
                    switch (propertyIndex) {
                    case 0:
                        rc = sortByString(mn1.getUmsetzung(), mn2.getUmsetzung());
                        break;
                    case 1:
                        rc = sortByDate(mn1.getUmsetzungBis(), mn2.getUmsetzungBis());
                        break;
                    case 2:
                        rc = sortByString(mn1.getUmsetzungDurch(), mn2.getUmsetzungDurch());
                        break;
                    case 3:
                        rc = sortByString(String.valueOf(mn1.getStufe()),
                                String.valueOf(mn2.getStufe()));
                        break;
                    case 4:
                        rc = sortByString(mn1.getParentTitle(), mn2.getParentTitle());
                        break;
                    case 5:
                        rc = sortByString(mn1.getTitle(), mn2.getTitle());
                        break;
                    default:
                        rc = 0;
                    }
                }
                // If descending order, flip the direction
                if (direction == DESCENDING) {
                    rc = -rc;
                }
            } catch (Exception e) {
                LOG.error("Error while sorting elements", e);
            }
            return rc;
        }

        protected int sortByString(String s1, String s2) {
            int rc = 0;
            if (s1 == null) {
                if (s2 != null) {
                    rc = 1;
                }
            } else if (s2 == null) {
                rc = -1;
            } else {
                rc = s1.compareTo(s2);
            }
            return rc;
        }

        protected int sortByDate(Date date1, Date date2) {
            if (date1 == null) {
                if (date2 == null) {
                    return 0;
                } else {
                    return 1;
                }
            }
            if (date2 == null) {
                return -1;
            }
            return date1.compareTo(date2);
        }

        public int getPropertyIndex() {
            return propertyIndex;
        }

        public int getDirection() {
            return direction;
        }
    }

    public void setLoadBlockNumber(int loadBlockNumber) {
        this.loadBlockNumber = loadBlockNumber;
    }

    public TableViewer getViewer() {
        return viewer;
    }

    public Action getLoadMoreAction() {
        return loadMoreAction;
    }

    private final class LoadCompounds extends WorkspaceJob {
        private final ITVerbund compound;

        private LoadCompounds(String name, ITVerbund compound) {
            super(name);
            this.compound = compound;
        }

        @Override
        public IStatus runInWorkspace(final IProgressMonitor monitor) {
            Activator.inheritVeriniceContextState();

            try {
                monitor.setTaskName(""); //$NON-NLS-1$
                LoadCnATreeElementTitles<ITVerbund> compoundLoader = new LoadCnATreeElementTitles<>(
                        ITVerbund.class);
                compoundLoader = ServiceFactory.lookupCommandService()
                        .executeCommand(compoundLoader);
                final List<ITVerbund> elements = compoundLoader.getElements();
                Display.getDefault().asyncExec(() -> {
                    compoundChoser.setElements(elements);
                    compoundChoser.setEnabled(true);

                    // Only try to preselect when a ITVerbund instance
                    // was
                    // given.
                    if (compound != null) {
                        compoundChoser.setSelectedCompound(compound);

                        // If the compoundChoser returns false here,
                        // then
                        // the compound does not exist anymore.
                        if (compoundChoser.isSelectedCompound(compound)) {
                            // Reload the measures belonging to the
                            // preselected compound.
                            loadMeasures(compound);

                            return;
                        }

                        // Compound not available anymore: Fall through.
                    }

                    // Place a message that asks the user to choose a
                    // compound.
                    viewer.setInput(new PlaceHolder(Messages.GenericMassnahmenView_9));
                });

            } catch (Exception e) {
                ExceptionUtil.log(e, Messages.GenericMassnahmenView_10);
            }
            return Status.OK_STATUS;
        }
    }

    private final class LoadMeasures extends WorkspaceJob {
        private final ITVerbund itVerbund;

        private LoadMeasures(String name, ITVerbund itVerbund) {
            super(name);
            this.itVerbund = itVerbund;
        }

        @Override
        public IStatus runInWorkspace(final IProgressMonitor monitor) {
            Activator.inheritVeriniceContextState();

            try {
                monitor.setTaskName(getMeasureLoadTaskLabel());
                loadBlockNumber++;
                Properties filter = new Properties();
                filter.put(FindMassnahmenForITVerbund.FILTER_DATE, getDateProperty());
                if (isDateSet) {
                    filter.put(getDateProperty(), getDateProperty());
                }
                if (umsetzungFilter.getUmsetzungPatternSet() != null) {
                    filter.put(MassnahmenUmsetzung.P_UMSETZUNG,
                            umsetzungFilter.getUmsetzungPatternSet());
                }
                if (siegelFilter.getPatternSet() != null) {
                    filter.put(MassnahmenUmsetzung.P_SIEGEL, siegelFilter.getPatternSet());
                }
                FindMassnahmenForITVerbund command = new FindMassnahmenForITVerbund(
                        itVerbund.getDbId(), loadBlockNumber, filter, getSortByProperty());
                command = ServiceFactory.lookupCommandService().executeCommand(command);
                final int number = command.getNumber();
                if (loadBlockNumber == 1) {
                    allMassnahmen = command.getAll();
                } else {
                    allMassnahmen.addAll(command.getAll());
                }
                Display.getDefault().asyncExec(() -> {
                    if (viewer.getControl().isDisposed()) {
                        return;
                    }
                    viewer.setInput(allMassnahmen);
                    compoundChoser.setEnabled(true);
                    int loaded = loadBlockNumber * FindMassnahmenForITVerbund.LOAD_BLOCK_SIZE;
                    if (loaded > number) {
                        loaded = number;
                        GenericMassnahmenView.this.loadMoreAction.setEnabled(false);
                    }
                    String info = "(" + loaded + " of " + number + ")";
                    GenericMassnahmenView.this.loadMoreAction.setText(info);
                });
            } catch (Exception e) {
                Display.getDefault().asyncExec(() -> compoundChoser.setEnabled(true));
                LOG.error("Error while loading massnahmen", e);
                ExceptionUtil.log(e, getTaskErrorLabel());
            }
            return Status.OK_STATUS;
        }
    }

}
