package com.logmaster.domain.savedata;

import com.logmaster.domain.Task;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

@Getter
@ToString
public class SaveData extends BaseSaveData {
    public final static int VERSION = 2;

    public SaveData() {
        this.version = VERSION;
    }

    private final Set<String> completedTasks = new HashSet<>();

    @Setter
    private @Nullable Task activeTask = null;

    @Setter
    private int rerolls = 0;
}
