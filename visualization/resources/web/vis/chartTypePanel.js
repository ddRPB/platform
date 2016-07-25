Ext4.define('LABKEY.vis.ChartTypePanel', {
    extend: 'Ext.panel.Panel',

    cls: 'chart-type-panel',
    layout: 'border',
    border: false,
    width: 900,
    height: 620,

    selectedType: null,
    selectedFields: null,
    requiredFieldNames: null,
    restrictColumnsEnabled: false,

    initComponent : function()
    {
        var typesArr = [
            {
                name: 'bar',
                title: 'Bar',
                //active: true,
                imgUrl: LABKEY.contextPath + '/visualization/images/barchart.png',
                fields: [
                    {name: 'x', label: 'X Axis Grouping', required: true, nonNumericOnly: true},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true}
                ]
            },
            {
                name: 'box',
                title: 'Box',
                active: true,
                imgUrl: LABKEY.contextPath + '/visualization/images/boxplot.png',
                fields: [
                    {name: 'x', label: 'X Axis Grouping'},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true},
                    {name: 'color', label: 'Color', nonNumericOnly: true},
                    {name: 'shape', label: 'Shape', nonNumericOnly: true}
                ]
            },
            {
                name: 'pie',
                title: 'Pie',
                //active: true,
                imgUrl: LABKEY.contextPath + '/visualization/images/piechart.png',
                fields: [
                    {name: 'x', label: 'Grouping', required: true, nonNumericOnly: true}
                ]
            },
            {
                name: 'scatter',
                title: 'Scatter',
                active: true,
                imgUrl: LABKEY.contextPath + '/visualization/images/scatterplot.png',
                fields: [
                    {name: 'x', label: 'X Axis', required: true},
                    {name: 'y', label: 'Y Axis', required: true, numericOnly: true},
                    {name: 'color', label: 'Color', nonNumericOnly: true},
                    {name: 'shape', label: 'Shape', nonNumericOnly: true}
                ]
            },
            {
                name: 'time',
                title: 'Time',
                imgUrl: LABKEY.contextPath + '/visualization/images/timechart.png'
            }
        ];

        this.typesStore = Ext4.create('Ext.data.Store', {
            model: 'LABKEY.vis.ChartTypeModel',
            data: typesArr
        });

        // lookup type by name, default to the first active chart type if none selected/found
        if (Ext4.isString(this.selectedType))
            this.selectedType = this.typesStore.findRecord('name', this.selectedType, 0, false, true, true);
        if (!this.selectedType)
            this.selectedType = this.typesStore.findRecord('active', true);

        // if no selectedFields pass in, create an empty object
        if (this.selectedFields == null)
            this.selectedFields = {};

        this.items = [
            this.getTitlePanel(),
            this.getTypesPanel(),
            this.getFieldMappingPanel(),
            this.getButtonBar()
        ];

        this.callParent();

        this.addEvents('cancelclick', 'doneclick');
    },

    getTitlePanel : function()
    {
        if (!this.titlePanel)
        {
            this.titlePanel = Ext4.create('Ext.panel.Panel', {
                region: 'north',
                cls: 'region-panel title-panel',
                border: false,
                html: 'Create a plot'
            });
        }

        return this.titlePanel;
    },

    getTypesPanel : function()
    {
        if (!this.typesPanel)
        {
            var tpl = new Ext4.XTemplate(
                '<tpl for=".">',
                    '<div class="item {[this.getItemCls(values)]}" id="chart-type-{name}">',
                        '<img src="{imgUrl}" height="50" width="80"/>',
                        '<div>{title}</div>',
                    '</div>',
                '</tpl>',
                {
                    getItemCls : function(item) {
                        return item.active ? 'item-active' : 'item-disabled';
                    }
                }
            );

            this.typesPanel = Ext4.create('Ext.view.View', {
                region: 'west',
                cls: 'region-panel types-panel',
                store: this.typesStore,
                tpl: tpl,
                itemSelector: 'div.item',
                listeners: {
                    scope: this,
                    beforeitemclick: this.allowTypeSelect,
                    selectionchange: this.selectChartType,
                    viewready: function(view)
                    {
                        // select the initial type, if not null
                        if (this.selectedType != null)
                        {
                            view.getSelectionModel().select(this.selectedType);
                        }
                        else
                        {
                            this.getFieldSelectionsPanel().add({
                                html: 'Select a chart type.'
                            });
                        }
                    }
                }
            });
        }

        return this.typesPanel;
    },

    getFieldMappingPanel : function()
    {
        if (!this.fieldMappingPanel)
        {
            this.fieldMappingPanel = Ext4.create('Ext.panel.Panel', {
                region: 'center',
                cls: 'region-panel mapping-panel',
                border: false,
                layout: 'column',
                items: [{
                    columnWidth: 0.5,
                    cls: 'field-selection-col',
                    border: false,
                    items: [this.getFieldSelectionsPanel()]
                },{
                    columnWidth: 0.5,
                    cls: 'mapping-query-col',
                    border: false,
                    hidden: true,
                    items: [
                        {
                            cls: 'description',
                            border: false,
                            html: 'Select a column and drag it to the field selection area to apply it to the plot.'
                        },
                        this.getQueryColumnsPanel()
                    ]
                }]
            });
        }

        return this.fieldMappingPanel;
    },

    showQueryMappingPanelCol : function()
    {
        this.getQueryColumnsGrid().getSelectionModel().deselectAll();
        this.down('*[cls~=mapping-query-col]').show();
    },

    getQueryColumnsPanel : function()
    {
        if (!this.queryColumnsPanel)
        {
            this.queryColumnsPanel = Ext4.create('Ext.panel.Panel', {
                cls: 'query-columns',
                border: false,
                items: [this.getQueryColumnsGrid()]
            });
        }

        return this.queryColumnsPanel;
    },

    getQueryColumnsGrid : function()
    {
        if (!this.queryColumnsGrid)
        {
            var store = Ext4.create('Ext.data.Store', {
                model: 'LABKEY.vis.QueryColumnModel',
                proxy: {
                    type: 'memory',
                    reader: {
                        type: 'json'
                    }
                },
                listeners: {
                    load: function(store)
                    {
                        if (!this.restrictColumnsEnabled) // TODO make this a single filterBy function and move restrictColumnsEnabled check within function
                        {
                            store.filterBy(function(record){
                                return !record.get('hidden');
                            });
                        }
                        else
                        {
                            store.filterBy(function(record){
                                return !record.get('hidden') && (record.get('measure') || record.get('dimension'));
                            });
                        }
                    },
                    scope: this
                }
            });

            this.queryColumnsGrid = Ext4.create('Ext.grid.Panel', {
                store: store,
                autoScroll: true,
                height: 400,
                enableColumnHide: false,
                columns: [
                    {
                        header: 'Columns',
                        dataIndex: 'label',
                        flex: 1,
                        renderer: function(value)
                        {
                            return Ext4.util.Format.htmlEncode(value);
                        }
                    }
                ],
                viewConfig: { plugins: this.getGridViewDragPluginConfig() },
                stripeRows: true,
                selModel: new Ext4.selection.RowModel({ singleSelect: true }),
                allowDeselect: true,
                listeners: {
                    scope: this,
                    beforedeselect: function()
                    {
                        this.getFieldSelectionsPanel().destroyFieldSelectionDropTargets();
                    },
                    select: function(sm, selected, index)
                    {
                        var ddGroup = this.getGridViewDragPluginConfig().ddGroup;
                        this.getFieldSelectionsPanel().addFieldSelectionDropTargets(this.queryColumnsGrid, ddGroup, selected);
                    }
                }
            });
        }

        return this.queryColumnsGrid;
    },

    loadQueryColumns : function(columns)
    {
        this.getQueryColumnsGrid().getStore().loadRawData(columns);
    },

    getGridViewDragPluginConfig : function()
    {
        return {
            ddGroup: 'column-grid-to-field-selection',
            ptype: 'gridviewdragdrop',
            dragText: '1 column selected',
            enableDrop: false
        };
    },

    getFieldSelectionsPanel : function()
    {
        if (!this.fieldSelectionsPanel)
        {
            this.fieldSelectionsPanel = Ext4.create('LABKEY.vis.ChartTypeFieldSelectionsPanel', {
                chartType: this.selectedType,
                selection: this.selectedFields,
                listeners: {
                    scope: this,
                    selectionchange: this.fieldSelectionsChanged
                }
            });
        }

        return this.fieldSelectionsPanel;
    },

    getButtonBar : function()
    {
        if (!this.buttonBar)
        {
            this.buttonBar = Ext4.create('Ext.toolbar.Toolbar', {
                region: 'south',
                cls: 'region-panel button-bar',
                border: false,
                ui: 'footer',
                defaults: {width: 65},
                items: [
                    '->',
                    this.getCancelButton(),
                    this.getDoneButton()
                ]
            });
        }

        return this.buttonBar;
    },

    getCancelButton : function()
    {
        if (!this.cancelButton)
        {
            this.cancelButton = Ext4.create('Ext.button.Button', {
                text: 'Cancel',
                scope: this,
                handler: function ()
                {
                    // TODO revert the panel back to initial values and deselect any grid column
                    this.fireEvent('cancelclick', this);
                }
            });
        }

        return this.cancelButton;
    },

    getDoneButton : function()
    {
        if (!this.doneButton)
        {
            this.doneButton = Ext4.create('Ext.button.Button', {
                text: 'Done',
                scope: this,
                handler: function() {
                    if (this.hasAllRequiredFields())
                        this.fireEvent('doneclick', this, this.getValues());
                    else
                        this.getFieldSelectionsPanel().flagRequiredFields();
                }
            });
        }

        return this.doneButton;
    },

    allowTypeSelect : function(view, selected)
    {
        return selected.get('active');
    },

    selectChartType : function(view, selected)
    {
        if (Ext4.isArray(selected) && selected.length == 1)
        {
            this.selectedType = selected[0];
            this.getFieldSelectionsPanel().update(this.selectedType);
            this.showQueryMappingPanelCol();

            // clear the required field names array so that it is recomputed next time it is accessed
            this.requiredFieldNames = null;

            // reset the selectedFields object since the type change may have removed some
            this.selectedFields = this.getFieldSelectionsPanel().getSelection();
        }
    },

    getRequiredFieldNames : function()
    {
        if (this.requiredFieldNames == null && this.selectedType != null)
        {
            this.requiredFieldNames = [];
            Ext4.each(this.selectedType.get('fields'), function (field)
            {
                if (field.required)
                    this.requiredFieldNames.push(field.name);
            }, this);
        }

        return this.requiredFieldNames || [];
    },

    hasAllRequiredFields : function()
    {
        var requiredKeysInSelection = Ext4.Array.intersect(this.getRequiredFieldNames(), Object.keys(this.selectedFields));
        return this.getRequiredFieldNames().length == requiredKeysInSelection.length;
    },

    fieldSelectionsChanged : function()
    {
        this.selectedFields = this.getFieldSelectionsPanel().getSelection();
    },

    getValues : function()
    {
        return {
            type: this.selectedType != null ? this.selectedType.get('name') : null,
            fields: this.selectedFields
        }
    }
});

Ext4.define('LABKEY.vis.ChartTypeFieldSelectionsPanel', {
    extend: 'Ext.panel.Panel',

    cls: 'field-selections',
    border: false,
    defaults: {border: false},
    items: [],

    chartType: null,
    selection: null,
    fieldSelectionDropTargets: [],

    addFieldSelectionDropTargets : function(grid, ddGroup, selectedCol)
    {
        // destroy any previous drop targets based on the last selected column
        this.destroyFieldSelectionDropTargets();

        // enable drop target based on allowable column types for the given field
        var selectedColType = selectedCol.get('normalizedType');
        Ext4.each(this.query('panel'), function(fieldSelPanel)
        {
            if (fieldSelPanel.getAllowableTypes().indexOf(selectedColType) > -1)
            {
                var dropTarget = fieldSelPanel.createDropTarget(grid, ddGroup);
                this.fieldSelectionDropTargets.push(dropTarget);
            }
        }, this);
    },

    destroyFieldSelectionDropTargets : function()
    {
        // remove the cls uses for display and hide the drop text
        Ext4.each(this.query('panel'), function(fieldSelPanel)
        {
            fieldSelPanel.removeDropTargetCls();
        }, this);

        // destroy the actual DropTarget components
        Ext4.each(this.fieldSelectionDropTargets, function (dropTarget)
        {
            dropTarget.destroy();
        }, this);

        this.fieldSelectionDropTargets = [];
    },

    flagRequiredFields : function()
    {
        Ext4.each(this.query('panel'), function(fieldSelPanel)
        {
            fieldSelPanel.flagIfRequired();
        }, this);
    },

    update : function(chartType)
    {
        if (Ext4.isDefined(chartType))
            this.chartType = chartType;

        this.removeAll();

        this.add(Ext4.create('Ext.Component', {
            cls: 'type-title',
            html: this.chartType.get('title')
        }));

        Ext4.each(this.chartType.get('fields'), function(field)
        {
            this.add(Ext4.create('LABKEY.vis.ChartTypeFieldSelectionPanel', {
                field: field,
                selection: this.selection ? this.selection[field.name] : undefined
            }));
        }, this);

        this.add(Ext4.create('Ext.Component', {
            cls: 'type-footer',
            html: '* Required fields'
        }));
    },

    getSelection : function()
    {
        this.selection = {};

        Ext4.each(this.query('panel'), function(fieldSelPanel)
        {
            if (fieldSelPanel.getSelection() != null)
                this.selection[fieldSelPanel.field.name] = fieldSelPanel.getSelection();
        }, this);

        return this.selection;
    }
});

Ext4.define('LABKEY.vis.ChartTypeFieldSelectionPanel', {
    extend: 'Ext.panel.Panel',

    border: false,
    field: null,
    selection: null,
    allowableTypes: null,

    initComponent : function()
    {
        // if we have an initial selection, make sure the type is a match
        if (this.selection != null)
        {
            var selectionType = this.selection.normalizedType || this.selection.type;
            if (Ext4.isDefined(selectionType) && this.getAllowableTypes().indexOf(selectionType) == -1)
                this.selection = null;
        }

        this.items = [
            this.getFieldTitle(),
            this.getFieldArea(),
            this.getFieldAreaDropText()
        ];

        this.callParent();

        this.addEvents('selectionchange');
        this.enableBubble('selectionchange');
    },

    getFieldTitle : function()
    {
        if (!this.fieldTitleCmp)
        {
            this.fieldTitleCmp = Ext4.create('Ext.Component', {
                cls: 'field-title',
                border: false,
                html: this.field.label + (this.field.required ? ' *' : '')
            });
        }

        return this.fieldTitleCmp;
    },

    getFieldArea : function()
    {
        if (!this.fieldAreaCmp)
        {
            this.fieldAreaCmp = Ext4.create('Ext.view.View', {
                cls: 'field-area',
                minHeight: 50,
                tpl: new Ext4.XTemplate(
                    '<tpl if="name">',
                        '<div class="field-selection-display">',
                            '{label}',
                            '<div class="fa fa-times field-selection-remove"></div>',
                        '</div>',
                    '</tpl>'
                ),
                data: this.selection
            });

            this.fieldAreaCmp.on('refresh', function(view)
            {
                var removeEl = view.getEl().down('div.field-selection-remove');
                if (removeEl)
                {
                    removeEl.on('click', this.removeSelection, this);
                }
            }, this);
        }

        return this.fieldAreaCmp;
    },

    getFieldAreaDropText : function()
    {
        if (!this.fieldAreaDropTextCmp)
        {
            this.fieldAreaDropTextCmp = Ext4.create('Ext.Component', {
                cls: 'field-area-drop-text',
                html: 'Drag and drop column here',
                hidden: true
            })
        }

        return this.fieldAreaDropTextCmp;
    },

    createDropTarget : function(grid, ddGroup)
    {
        var me = this;

        this.addCls('drop-target');
        if (this.getSelection() == null)
            this.getFieldAreaDropText().show();

        return new Ext4.dd.DropTarget(this.getEl(), {
            ddGroup: ddGroup,
            notifyEnter: function(ddSource, e, data)
            {
                me.addCls('drop-target-over');
            },
            notifyOut: function(ddSource, e, data)
            {
                me.removeCls('drop-target-over');
            },
            notifyDrop: function(ddSource, e, data)
            {
                me.setSelection(data.records[0]);
                grid.getSelectionModel().deselectAll();
                return true;
            }
        });
    },

    removeDropTargetCls : function()
    {
        this.removeCls('drop-target');
        this.getFieldAreaDropText().hide();
        this.getEl().removeCls('drop-target-over');
    },

    flagIfRequired : function()
    {
        if (this.field.required && this.selection == null)
            this.addCls('missing-required');
    },

    removeSelection : function()
    {
        this.setSelection(null);

        if (this.hasCls('drop-target'))
            this.getFieldAreaDropText().show();
    },

    setSelection : function(column)
    {
        this.selection = Ext4.clone(column && column.data ? column.data : column);

        if (this.selection != null)
            this.removeCls('missing-required');

        this.getFieldArea().update(this.selection);
        this.getFieldArea().fireEvent('refresh', this.getFieldArea());
        this.fireEvent('selectionchange', this);
    },

    getSelection : function()
    {
        return this.selection;
    },

    getAllowableTypes : function()
    {
        if (this.allowableTypes == null)
        {
            var numericTypes = ['int', 'float', 'double'],
                nonNumericTypes = ['string', 'date', 'boolean'];

            if (this.field.numericOnly)
                this.allowableTypes = numericTypes;
            else if (this.field.nonNumericOnly)
                this.allowableTypes = nonNumericTypes;
            else
                this.allowableTypes = numericTypes.concat(nonNumericTypes);
        }

        return this.allowableTypes;
    }
});

Ext4.define('LABKEY.vis.ChartTypeModel', {
    extend: 'Ext.data.Model',
    fields: [
        {name: 'name', type: 'string'},
        {name: 'title', type: 'string'},
        {name: 'imgUrl', type: 'string'},
        {name: 'active', type: 'boolean'},
        // array of field selection object definitions of the type:
        // {name: 'x', label: 'X Axis Grouping', required: true, numericOnly: true, nonNumericOnly: true}
        {name: 'fields', defaultValue: []}
    ]
});

Ext4.define('LABKEY.vis.QueryColumnModel',{
    extend: 'Ext.data.Model',
    fields: [
        {name: 'label', mapping: 'shortCaption', type: 'string'},
        {name: 'name', type: 'string'},
        {name: 'hidden', type: 'boolean', defaultValue: false},
        {name: 'measure', type: 'boolean', defaultValue: false},
        {name: 'dimension', type: 'boolean', defaultValue: false},
        {name: 'type'},
        {name: 'displayFieldJsonType'},
        {name: 'normalizedType', convert: function(value, record){
            // We take the displayFieldJSONType if available because if the column is a look up the record.type will
            // always be INT. The displayFieldJSONType is the actual type of the lookup.
            if(record.data.displayFieldJsonType)
                return record.data.displayFieldJsonType;

            return record.data.type;
        }}
    ]
});