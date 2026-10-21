/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gearguide;

import static com.gearguide.Models.*;

import net.runelite.api.Client;
import net.runelite.api.gameval.*;

import java.util.List;

final class TaskReader {
    static SlayerTask read(Client client) {
        SlayerTask task = new SlayerTask();
        try {
            task.remaining = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
            if (task.remaining <= 0) {
                task.known = true;
                return task;
            }
            int id = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
            List<Integer> rows;
            int row;
            if (id == 98) {
                rows =
                        client.getDBRowsByValue(
                                DBTableID.SlayerTaskSublist.ID,
                                DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID,
                                0,
                                client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID));
                if (rows.isEmpty()) return task;
                row =
                        (Integer)
                                client.getDBTableField(
                                                rows.get(0),
                                                DBTableID.SlayerTaskSublist.COL_TASK,
                                                0)[0];
            } else {
                rows =
                        client.getDBRowsByValue(
                                DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, id);
                if (rows.isEmpty()) return task;
                row = rows.get(0);
            }
            task.name =
                    (String)
                            client.getDBTableField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0)[
                                    0];
            task.enemyType = enemyKey(task.name);
            int area = client.getVarpValue(VarPlayerID.SLAYER_AREA);
            if (area > 0) {
                rows =
                        client.getDBRowsByValue(
                                DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, area);
                if (!rows.isEmpty())
                    task.location =
                            (String)
                                    client.getDBTableField(
                                                    rows.get(0),
                                                    DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER,
                                                    0)[0];
            }
            task.known = true;
        } catch (RuntimeException ignored) {
            task.known = false;
        }
        return task;
    }
}
