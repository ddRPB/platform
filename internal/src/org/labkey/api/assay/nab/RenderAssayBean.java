package org.labkey.api.assay.nab;

import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionAssayRun;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.dilution.DilutionDataHandler;
import org.labkey.api.assay.dilution.DilutionSummary;
import org.labkey.api.assay.nab.view.DuplicateDataFileRunView;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ExperimentRunListView;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.nab.NabUrls;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* Created by IntelliJ IDEA.
* User: klum
* Date: 5/15/13
*/
public class RenderAssayBean extends RenderAssayForm
{
    private ViewContext _context;
    private DilutionAssayRun _assay;
    private boolean _printView;
    private Set<String> _hiddenRunColumns;
    private Map<String, Object> _displayProperties;
    private Boolean _dupFile = null;
    private int _graphHeight = 300;
    private int _graphWidth = 275;
    private int _maxSamplesPerGraph = 8;
    private int _graphsPerRow = 2;
    private String _sampleNoun = "Sample";
    private String _neutralizationAbrev = "Neut.";
    private ActionURL _graphURL;

    public RenderAssayBean()
    {
        _hiddenRunColumns = new HashSet<String>();
        _hiddenRunColumns.add(ExpRunTable.Column.RunGroups.name());
        _hiddenRunColumns.add(ExpRunTable.Column.Links.name());
        _hiddenRunColumns.add(ExpRunTable.Column.Flag.name());
        _hiddenRunColumns.add(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        _hiddenRunColumns.add(AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME);
        _hiddenRunColumns.addAll(Arrays.asList(DilutionAssayProvider.CUTOFF_PROPERTIES));
    }

    public Map<String, Object> getRunDisplayProperties()
    {
        if (_displayProperties == null)
        {
            Map<PropertyDescriptor, Object> allProperties = _assay.getRunDisplayProperties(_context);
            _displayProperties = new LinkedHashMap<String, Object>();
            for (Map.Entry<PropertyDescriptor, Object> entry : allProperties.entrySet())
            {
                PropertyDescriptor property = entry.getKey();
                if (!_hiddenRunColumns.contains(property.getName()))
                {
                    if (DilutionAssayProvider.CURVE_FIT_METHOD_PROPERTY_NAME.equals(property.getName()) && _fitType != null)
                    {
                        _displayProperties.put(property.getNonBlankCaption(), _fitType.getLabel());
                    }
                    else
                    {
                        Object value = entry.getValue();
                        if (value != null)
                        {
                            _displayProperties.put(property.getNonBlankCaption(), formatValue(property, value));
                        }
                    }
                }
            }
        }
        return _displayProperties;
    }

    public Object formatValue(PropertyDescriptor pd, Object value)
    {
        if (pd.getFormat() != null)
        {
            if (pd.getPropertyType() == PropertyType.DOUBLE)
            {
                DecimalFormat format = new DecimalFormat(pd.getFormat());
                value = value == null ? "" : format.format(value);
            }
            if (pd.getPropertyType() == PropertyType.DATE_TIME)
            {
                DateFormat format = new SimpleDateFormat(pd.getFormat());
                value = value == null ? "" : format.format((Date) value);
            }
        }
        else if (pd.getPropertyType() == PropertyType.DATE_TIME && value instanceof Date)
        {
            Date date = (Date) value;
            if (date.getHours() == 0 &&
                    date.getMinutes() == 0 &&
                    date.getSeconds() == 0)
            {
                value = DateUtil.formatDate(date);
            }
            else
            {
                value = DateUtil.formatDateTime(date);
            }
        }
        return value;
    }


    public List<DilutionAssayRun.SampleResult> getSampleResults()
    {
        return _assay.getSampleResults();
    }

    public DilutionAssayRun getAssay()
    {
        return _assay;
    }

    public void setAssay(DilutionAssayRun assay)
    {
        _assay = assay;
    }

    private boolean isDuplicateDataFile()
    {
        if (_dupFile == null)
        {
            ResultSet rs = null;
            try
            {
                SimpleFilter filter = new SimpleFilter("ProtocolLsid", _assay.getProtocol().getLSID());
                filter.addCondition("Name", _assay.getDataFile().getName());
                filter.addCondition("RowId", _assay.getRun().getRowId(), CompareType.NEQ);
                rs = Table.select(ExperimentService.get().getTinfoExperimentRun(), Table.ALL_COLUMNS, filter, null);
                _dupFile = rs.next();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            finally
            {
                if (rs != null)
                    try { rs.close(); } catch (SQLException e) { /* fall through */ }
            }
        }
        return _dupFile;
    }

    public QueryView getDuplicateDataFileView(ViewContext context)
    {
        if (isDuplicateDataFile())
        {
            ExpProtocol protocol = _assay.getProtocol();
            AssayProtocolSchema schema = AssayService.get().getProvider(protocol).createProtocolSchema(context.getUser(), context.getContainer(), protocol, null);
            QuerySettings setting = ExperimentRunListView.getRunListQuerySettings(schema, context, AssayProtocolSchema.RUNS_TABLE_NAME, true);

            return new DuplicateDataFileRunView(schema, setting, _assay, _assay.getRun());
        }
        else
            return null;
    }

    public HttpView getControlsView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/controlSummary.jsp", this);
    }

    public HttpView getCutoffsView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/cutoffDilutions.jsp", this);
    }

    public HttpView getGraphView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/runGraph.jsp", this);
    }

    public HttpView getSamplePropertiesView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/sampleProperties.jsp", this);
    }

    public HttpView getRunPropertiesView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/runProperties.jsp", this);
    }

    public HttpView getSampleDilutionsView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/sampleDilutions.jsp", this);
    }

    public HttpView getPlateDataView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/plateData.jsp", this);
    }

    public HttpView getRunNotesView()
    {
        return new JspView<RenderAssayBean>("/org/labkey/api/assay/nab/view/runNotes.jsp", this);
    }

    public boolean needsCurveNote()
    {
        return _assay.getRenderedCurveFitType() != _assay.getSavedCurveFitType();
    }

    public boolean needsNewRunNote()
    {
        return  !isPrintView() && isNewRun();
    }

    public boolean needsDupFileNote()
    {
        return !isPrintView() &&  isDuplicateDataFile();
    }

    public boolean needsNotesView()
    {
        return needsCurveNote() || needsNewRunNote() || needsDupFileNote();
    }

    public boolean isPrintView()
    {
        return _printView;
    }

    public HttpView getDiscussionView(ViewContext context)
    {
        ExpRun run = _assay.getRun();
        ActionURL pageUrl = context.getActionURL().clone();
        pageUrl.replaceParameter("rowId", "" + run.getRowId());
        String discussionTitle = "Discuss Run " + run.getRowId() + ": " + run.getName();
        String entityId = run.getLSID();
        DiscussionService.Service service = DiscussionService.get();
        return service.getDisussionArea(context,
                entityId, pageUrl, discussionTitle, true, false);
    }

    public int getRunId()
    {
        return _assay.getRun().getRowId();
    }

    public Pair<PropertyDescriptor, Object> getFitError(DilutionAssayRun.SampleResult result, Container container)
    {
        try
        {
            Lsid fitErrorURI = new Lsid(DilutionDataHandler.NAB_PROPERTY_LSID_PREFIX, getAssay().getProtocol().getName(), DilutionDataHandler.FIT_ERROR_PROPERTY);
            PropertyDescriptor fitErrorPd =
                    _assay.getDataHandler().getPropertyDescriptor(container, getAssay().getProtocol(), DilutionDataHandler.FIT_ERROR_PROPERTY, new HashMap<Integer, String>());
            if (null != fitErrorPd)
                return new Pair<PropertyDescriptor, Object>(fitErrorPd, result.getDilutionSummary().getFitError());
        }
        catch (DilutionCurve.FitFailedException e)
        {       // ignore
        }
        return null;
    }

    public Pair<PropertyDescriptor, Object> getStandardDev(DilutionAssayRun.SampleResult result, Container container)
    {
        PropertyDescriptor stdDevPd =
                _assay.getDataHandler().getPropertyDescriptor(container, getAssay().getProtocol(), DilutionDataHandler.STD_DEV_PROPERTY_NAME, new HashMap<Integer, String>());
        if (null != stdDevPd)
        {
            DilutionSummary summary = result.getDilutionSummary();
            return new Pair<PropertyDescriptor, Object>(stdDevPd, summary.getFirstWellGroup().getStdDev());
        }
        return null;
    }

    public Pair<PropertyDescriptor, Object> getAuc(DilutionAssayRun.SampleResult result, Container container)
    {
        String aucPropertyName = getFitType() == null ? DilutionDataHandler.AUC_PREFIX : getAssay().getDataHandler().getPropertyName(DilutionDataHandler.AUC_PREFIX, getFitTypeEnum());
        PropertyDescriptor aucPD = _assay.getDataHandler().getPropertyDescriptor(container, getAssay().getProtocol(), aucPropertyName, new HashMap<Integer, String>());
        if (null != aucPD)
            return new Pair<PropertyDescriptor, Object>(aucPD, result.getDataProperty(aucPropertyName));
        return null;
    }

    public Pair<PropertyDescriptor, Object> getPositiveAuc(DilutionAssayRun.SampleResult result, Container container)
    {
        String aucPropertyName = getFitType() == null ? DilutionDataHandler.pAUC_PREFIX : getAssay().getDataHandler().getPropertyName(DilutionDataHandler.pAUC_PREFIX, getFitTypeEnum());
        PropertyDescriptor aucPD = _assay.getDataHandler().getPropertyDescriptor(container, getAssay().getProtocol(), aucPropertyName, new HashMap<Integer, String>());
        if (null != aucPD)
            return new Pair<PropertyDescriptor, Object>(aucPD, result.getDataProperty(aucPropertyName));
        return null;
    }

    public int getGraphHeight()
    {
        return _graphHeight;
    }

    public void setGraphHeight(int graphHeight)
    {
        _graphHeight = graphHeight;
    }

    public int getGraphWidth()
    {
        return _graphWidth;
    }

    public void setGraphWidth(int graphWidth)
    {
        _graphWidth = graphWidth;
    }

    public int getMaxSamplesPerGraph()
    {
        return _maxSamplesPerGraph;
    }

    public void setMaxSamplesPerGraph(int maxSamplesPerGraph)
    {
        _maxSamplesPerGraph = maxSamplesPerGraph;
    }

    public int getGraphsPerRow()
    {
        return _graphsPerRow;
    }

    public void setGraphsPerRow(int graphsPerRow)
    {
        _graphsPerRow = graphsPerRow;
    }

    public void setPrintView(boolean printView)
    {
        _printView = printView;
    }

    public void setContext(ViewContext context)
    {
        _context = context;
    }

    public String getSampleNoun()
    {
        return _sampleNoun;
    }

    public void setSampleNoun(String sampleNoun)
    {
        _sampleNoun = sampleNoun;
    }

    public String getNeutralizationAbrev()
    {
        return _neutralizationAbrev;
    }

    public void setNeutralizationAbrev(String neutralizationAbrev)
    {
        _neutralizationAbrev = neutralizationAbrev;
    }

    public ActionURL getGraphURL()
    {
        if (_graphURL == null && _context != null)
            _graphURL = PageFlowUtil.urlProvider(NabUrls.class).urlGraph(_context.getContainer());

        return _graphURL;
    }

    public void setGraphURL(ActionURL graphURL)
    {
        _graphURL = graphURL;
    }
}
