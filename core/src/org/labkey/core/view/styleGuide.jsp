<%
    /*
     * Copyright (c) 2005-2016 LabKey Corporation
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
%>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Ext3");
        dependencies.add("Ext4");
    }
%>
<style type="text/css">
    <%-- Style Guide Only CSS --%>
    .lk-sg-section { display: none; }
    .lk-sg-example { margin-bottom: 10px; }
</style>
<div class="hidden-xs hidden-sm col-md-3 col-lg-3">
    <div id="lk-sg-nav" class="list-group">
        <a href="#overview" class="list-group-item">Overview</a>
        <a href="#scaffolding" class="list-group-item">Scaffolding</a>
        <a href="#layout" class="list-group-item">Layout</a>
        <a href="#type" class="list-group-item">Typography</a>
        <a href="#buttons" class="list-group-item">Buttons</a>
        <a href="#forms" class="list-group-item">Forms</a>
        <a href="#ext3" class="list-group-item">ExtJS 3</a>
        <a href="#ext4" class="list-group-item">ExtJS 4</a>
    </div>
</div>
<div class="col-xs-12 col-sm-12 col-md-9 col-lg-9">
    <labkey:panel id="type" className="lk-sg-section">
        <h1 class="page-header">Typography</h1>
        <table>
            <tbody>
                <tr><td><h1>h1. LabKey Heading</h1></td></tr>
                <tr><td><h2>h2. LabKey Heading</h2></td></tr>
                <tr><td><h3>h3. LabKey Heading</h3></td></tr>
                <tr><td><h4>h4. LabKey Heading</h4></td></tr>
                <tr><td><h5>h5. LabKey Heading</h5></td></tr>
                <tr><td><h6>h6. LabKey Heading</h6></td></tr>
            </tbody>
        </table>
        <h3>Elements</h3>
        <p>Different types of elements</p>
        <table class="table">
            <tbody>
            <tr><td><strong>strong. Display some text.</strong></td></tr>
            <tr><td><p>p. Display some text.</p></td></tr>
            <tr><td><em>em. Display some text.</em></td></tr>
            </tbody>
        </table>
    </labkey:panel>
    <%--<div class="dropdown">--%>
        <%--<button id="dLabel" type="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">--%>
            <%--JavaScript Working!--%>
            <%--<span class="caret"></span>--%>
        <%--</button>--%>
        <%--<ul class="dropdown-menu" aria-labelledby="dLabel">--%>
            <%--<li><a href="#">Action</a></li>--%>
            <%--<li><a href="#">Action 2</a></li>--%>
            <%--<li><a href="#">Action 3</a></li>--%>
            <%--<li><a href="#">Action 4</a></li>--%>
        <%--</ul>--%>
    <%--</div>--%>
    <div class="lk-sg-section">
        <h2 class="page-header">Typography</h2>
        <h3>Headings</h3>
        <p>Displays all the headers</p>
        <div class="lk-sg-example lk-sg-example-typography">
            <table class="table">
                <tbody>
                <tr><td><h1>h1. LabKey Heading</h1></td></tr>
                <tr><td><h2>h2. LabKey Heading</h2></td></tr>
                <tr><td><h3>h3. LabKey Heading</h3></td></tr>
                <tr><td><h4>h4. LabKey Heading</h4></td></tr>
                <tr><td><h5>h5. LabKey Heading</h5></td></tr>
                <tr><td><h6>h6. LabKey Heading</h6></td></tr>
                </tbody>
            </table>
        </div>
        <h3>Elements</h3>
        <p>Different types of elements</p>
        <div class="lk-sg-example lk-sg-example-typography">
            <table class="table">
                <tbody>
                <tr><td><strong>strong. Display some text.</strong></td></tr>
                <tr><td><p>p. Display some text.</p></td></tr>
                <tr><td><em>em. Display some text.</em></td></tr>
                </tbody>
            </table>
        </div>
    </div>
    <labkey:panel id="buttons" className="lk-sg-section">
        <h1 class="page-header">Buttons</h1>
        <p>btn-default buttons using &lt;a&gt;, &lt;button&gt;, or &lt;input&gt; element.</p>
        <div class="lk-sg-example">
            <a class="btn btn-default" href="#" role="button">Link</a>
            <button class="btn btn-default" type="submit">Button</button>
            <input class="btn btn-default" type="button" value="Input">
            <input class="btn btn-default" type="submit" value="Submit">
        </div>
        <p>btn-primary buttons using &lt;a&gt;, &lt;button&gt;, or &lt;input&gt; element.</p>
        <div class="lk-sg-example">
            <a class="btn btn-primary" href="#" role="button">Link</a>
            <button class="btn btn-primary" type="submit">Button</button>
            <input class="btn btn-primary" type="button" value="Input">
            <input class="btn btn-primary" type="submit" value="Submit">
        </div>
        <p>Use any of the available button classes to quickly create a styled button.</p>
        <div class="lk-sg-example">
            <button type="button" class="btn btn-default">Default</button>
            <button type="button" class="btn btn-primary">Primary</button>
            <button type="button" class="btn btn-success">Success</button>
            <button type="button" class="btn btn-info">Info</button>
            <button type="button" class="btn btn-warning">Warning</button>
            <button type="button" class="btn btn-danger">Danger</button>
            <button type="button" class="btn btn-link">Link</button>
        </div>
        <p>Different sizes of buttons.</p>
        <div class="lk-sg-example">
            <p>
                <button type="button" class="btn btn-primary btn-lg">Large button</button>
                <button type="button" class="btn btn-default btn-lg">Large button</button>
            </p>
            <p>
                <button type="button" class="btn btn-primary">Default button</button>
                <button type="button" class="btn btn-default">Default button</button>
            </p>
            <p>
                <button type="button" class="btn btn-primary btn-sm">Small button</button>
                <button type="button" class="btn btn-default btn-sm">Small button</button>
            </p>
            <p>
                <button type="button" class="btn btn-primary btn-xs">Extra small button</button>
                <button type="button" class="btn btn-default btn-xs">Extra small button</button>
            </p>
        </div>
        <p>Basic button group.</p>
        <div class="lk-sg-example">
            <div class="btn-group" role="group" aria-label="Basic Example">
                <button type="button" class="btn btn-default">Left</button>
                <button type="button" class="btn btn-default">Middle</button>
                <button type="button" class="btn btn-default">Right</button>
            </div>
        </div>
    </labkey:panel>
    <labkey:panel id="forms" className="lk-sg-section">
        <h1 class="page-header">Forms</h1>
        <p>Horizontal form</p>
        <div class="lk-sg-example">
            <labkey:form action="some-action" className="form-horizontal">
                <labkey:input name="name" label="Name" placeholder="M Beaker" id="exampleInputName1"/>
                <labkey:input name="email" label="Email address" placeholder="beaker@labkey.com" id="exampleInputName1"/>
                <labkey:input name="avatar" label="Avatar" type="file" id="avatar1" message="A special message about the avatar field"/>
                <button type="submit" class="btn btn-default">Invite</button>
            </labkey:form>
        </div>
        <p>Inline form</p>
        <div class="lk-sg-example">
            <form action="some-action" class="form-inline">
                <div class="form-group">
                    <label for="exampleInputName4" class="control-label">Name</label>
                    <input type="text" class="form-control" name="name" id="exampleInputName4" placeholder="M Beaker">
                </div>
                <div class="form-group">
                    <label for="exampleInputEmail4" class="control-label">Email address</label>
                    <input type="email" class="form-control" name="email" id="exampleInputEmail4" placeholder="beaker@labkey.com">
                </div>
                <button type="submit" class="btn btn-default">Invite</button>
            </form>
            <labkey:form action="some-action" className="form-inline">
                <labkey:input name="name" label="Name" placeholder="M Beaker" id="exampleInputName2"/>
                <labkey:input name="email" label="Email address" placeholder="beaker@labkey.com" id="exampleInputName2" type="email"/>
                <button type="submit" class="btn btn-default">Invite</button>
            </labkey:form>
        </div>
        <p>LabKey property form using tables (DEPRECATED)</p>
        <div class="lk-sg-example">
            <form action="labkey">
                <table width="100%" cellpadding="0">
                    <tr>
                        <td class="labkey-form-label">Header short name (appears in every page header and in emails)</td>
                        <td><input type="text" name="systemShortName" size="50" value="LabKey Server"></td>
                    </tr>
                </table>
            </form>
        </div>
    </labkey:panel>
    <labkey:panel id="ext3" className="lk-sg-section">
        <h1 class="page-header">ExtJS 3.4.1</h1>
        <div class="lk-sg-example">
            <div class="lk-sg-example-ext3">
                <div id="ext3-panel" class="extContainer"></div>
                <div id="ext3-button" class="extContainer"></div>
                <div id="ext3-dialog" class="extContainer"></div>
            </div>
            <script type="application/javascript">
                if (typeof Ext !== 'undefined') {
                    Ext.onReady(function() {
                        var panel = new Ext.Panel({
                            renderTo: 'ext3-panel',
                            title: 'Ext 3 Panel',
                            html: 'Body',
                            bbar: [{
                                text: 'Button One'
                            },{
                                text: 'Button Two'
                            },{
                                text: 'Button Three'
                            }]
                        });

                        var btn = new Ext.Button({
                            renderTo: 'ext3-button',
                            text: 'Ext 3 Button'
                        });

                        var dialog = new Ext.Button({
                            renderTo: 'ext3-dialog',
                            text: 'See Modal Window',
                            handler: function() {
                                var win = new Ext.Window({
                                    title: 'Ext 3 Window',
                                    height: 300,
                                    width: 400,
                                    modal: true,
                                    html: 'Content',
                                    buttons: [{
                                        text: 'Cancel'
                                    },{
                                        text: 'Ok'
                                    }]
                                }).show();
                            }
                        });
                    });
                }
                else {
                    document.getElementById('ext3-panel').innerHTML = 'Ext 3 is not available.'
                }
            </script>
        </div>
    </labkey:panel>
    <labkey:panel id="ext4" className="lk-sg-section">
        <h1 class="page-header">ExtJS 4.2.1</h1>
        <div class="lk-sg-example">
            <div class="lk-sg-example-ext3">
                <div id="ext4-panel"></div>
                <div id="ext4-button"></div>
                <div id="ext4-dialog"></div>
                <div id="ext4-form"></div>
            </div>
            <script type="application/javascript">
                if (typeof Ext4 !== 'undefined') {
                    +function($) {
                        function display() {
                            Ext4.create('Ext.Panel', {
                                id: 'yolo',
                                renderTo: 'ext4-panel',
                                title: 'Ext 4 Panel',
                                html: 'Body',
                                bbar: [{
                                    text: 'Button One'
                                },{
                                    text: 'Button Two'
                                },{
                                    text: 'Button Three'
                                }]
                            });

                            Ext4.create('Ext.Button', {
                                renderTo: 'ext4-button',
                                text: 'Ext 4 Button'
                            });

                            Ext4.create('Ext.Button', {
                                renderTo: 'ext4-dialog',
                                text: 'See Modal Window',
                                handler: function() {
                                    Ext4.create('Ext.Window', {
                                        title: 'Ext 4 Window',
                                        height: 300,
                                        width: 400,
                                        modal: true,
                                        html: 'Content',
                                        autoShow: true
                                    });
                                }
                            });

                            Ext4.create('Ext.Panel', {
                                width: 500,
                                height: 300,
                                title: "Ext4 Form",
                                layout: 'form',
                                renderTo: 'ext4-form',
                                bodyPadding: 20,
                                defaultType: 'textfield',
                                dockedItems: [{
                                    xtype: 'toolbar',
                                    dock: 'bottom',
                                    items: [{
                                        text: 'Save'
                                    },{
                                        text: 'Cancel'
                                    }]
                                }],
                                items: [{
                                    fieldLabel: 'First Name',
                                    name: 'first',
                                    allowBlank:false
                                },{
                                    fieldLabel: 'Last Name',
                                    name: 'last'
                                },{
                                    fieldLabel: 'Order Date',
                                    name: 'date',
                                    xtype: 'datefield'
                                },{
                                    fieldLabel: 'Quantity',
                                    name: 'quantity',
                                    xtype: 'numberfield'
                                },{
                                    xtype: 'timefield',
                                    fieldLabel: 'Order Time',
                                    name: 'time',
                                    minValue: '8:00am',
                                    maxValue: '6:00pm'
                                },{
                                    xtype: 'checkboxgroup',
                                    fieldLabel: 'Toppings',
                                    name: 'order',
                                    items: [{
                                        boxLabel: 'Anchovies',
                                        name: 'topping',
                                        inputValue: '1'
                                    },{
                                        boxLabel: 'Artichoke Hearts',
                                        name: 'topping',
                                        inputValue: '2',
                                        checked: true
                                    },{
                                        boxLabel: 'Bacon',
                                        name: 'topping',
                                        inputValue: '3'
                                    }]
                                },{
                                    fieldLabel: 'Spicy',
                                    xtype: 'radiogroup',
                                    allowBlank: false,
                                    name: 'spicy',
                                    layout: 'hbox',
                                    items: [{
                                        boxLabel: 'yes',
                                        checked: true
                                    },{
                                        boxLabel: 'no'
                                    }]
                                }]
                            });
                        }

                        // the following just attempts to ensure that ExtJS 4 is both ready and the current
                        // section (ext4) is loaded prior to display.
                        function onHash() {
                            if (window.location.hash === '#ext4') {
                                Ext4.onReady(function() { Ext4.defer(display, 250); });
                                $(window).off('hashchange', onHash);
                            }
                        }

                        if (window.location.hash === '#ext4') {
                            onHash();
                        }
                        else {
                            $(window).on('hashchange', onHash);
                        }
                    }(jQuery);
                }
                else {
                    document.getElementById('ext4-panel').innerHTML = 'Ext 4 is not available.'
                }
            </script>
        </div>
    </labkey:panel>
</div>
<script type="text/javascript">
    +function($) {

        var defaultRoute = "type";

        function loadRoute(hash) {
            if (!hash || hash === '#') {
                hash = '#' + defaultRoute;
            }

            $('#lk-sg-nav').find('a').removeClass('active');
            $('#lk-sg-nav').find('a[href=\'' + hash + '\']').addClass('active');
            $('.lk-sg-section').hide();
            $('.lk-sg-section[id=\'' + hash.replace('#', '') + '\']').show();
        }

        $(window).on('hashchange', function() {
            loadRoute(window.location.hash);
        });

        $(function() {
            loadRoute(window.location.hash);
        });
    }(jQuery);
</script>