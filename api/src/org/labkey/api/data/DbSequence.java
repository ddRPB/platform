/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.util.ContextListener;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ShutdownListener;

/**
 * User: adam
 * Date: 4/6/13
 * Time: 2:16 PM
 */
public class DbSequence
{
    private final Container _c;
    private final int _rowId;
    private final String _name;

    DbSequence(Container c, String name, int rowId)
    {
        _c = c;
        _name = name;
        _rowId = rowId;
    }

    public String getName()
    {
        return _name;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public Container getContainer()
    {
        return _c;
    }

    public int current()
    {
        return DbSequenceManager.current(this);
    }

    public int next()
    {
        return DbSequenceManager.next(this);
    }

    public void ensureMinimum(int minimum)
    {
        DbSequenceManager.ensureMinimum(this, minimum);
    }

    @Override
    public String toString()
    {
        return _c.toString() + ": " + _rowId;
    }


    /** there are two ways we could write this
     * a) Support multiple DbSequence.Preallocate for the same sequence instance (e.g. rowid)
     *      potentially each would not have to be thread safe, but I think this would tend to generate lots of missing values
     * b) Support exactly 0-1 DbSequence.Preallocate for one sequence instance (e.g. rowid)
     *      just have to be thread safe is all, but how do we enforce the "exactly one per" rule?
     * NOTE: going with B
     */
    public static class Preallocate extends DbSequence implements ShutdownListener
    {
        private Integer _currentValue = null;
        private Integer _lastReservedValue = null;

        // CONSIDER use a Lock instead of synchronization?  I don't think DbSequenceManager ever deadlocks...
        // private Lock _lock = new ReentrantLock();

        Preallocate(Container c, String name, int rowId)
        {
            super(c, name, rowId);
            ContextListener.addShutdownListener(this);
        }

        // move to DbSequenceManager?
        public void done()
        {
            ContextListener.removeShutdownListener(this);
        }

        public synchronized int current()
        {
            if (null != _currentValue)
                return _currentValue;
            return DbSequenceManager.current(this);
        }

        public synchronized int next()
        {
            if (null == _lastReservedValue || _currentValue >= _lastReservedValue)
            {
                Pair<Integer, Integer> reserved = DbSequenceManager.reserve(this,100);
                _currentValue = reserved.first;
                _lastReservedValue = reserved.second;
            }
            _currentValue += 1;
            return _currentValue;
        }

        public synchronized void ensureMinimum(int minimum)
        {
            if (null != _lastReservedValue && minimum <= _lastReservedValue)
            {
                _currentValue = Math.max(_currentValue, minimum);
                return;
            }
            DbSequenceManager.ensureMinimum(this, minimum);
            _currentValue = null;
            _lastReservedValue = null;
        }

        @Override
        public synchronized void shutdownPre()
        {
        }

        @Override
        public synchronized void shutdownStarted()
        {
            if (null != _currentValue)
                DbSequenceManager.setSequenceValue(this, _currentValue);
            _currentValue = null;
            _lastReservedValue = null;
        }
    }
}