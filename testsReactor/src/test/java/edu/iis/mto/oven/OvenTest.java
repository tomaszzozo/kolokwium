package edu.iis.mto.oven;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OvenTest {

    @Mock HeatingModule heatingModuleMock;
    @Mock Fan fanMock;

    private final ProgramStage standardStage = ProgramStage.builder()
            .withTargetTemp(180)
            .withStageTime(120)
            .withHeat(HeatType.HEATER)
            .build();
    private final BakingProgram standardBakingProgram = BakingProgram.builder()
            .withCoolAtFinish(false)
            .withInitialTemp(0)
            .withStages(Collections.singletonList(standardStage))
            .build();

    private Oven oven;

    @BeforeEach
    void setUp() {
        oven = new Oven(heatingModuleMock, fanMock);
    }

    @Test
    void constructorHeatingModuleNull() {
        assertThrows(NullPointerException.class, () -> new Oven(null, fanMock));
    }

    @Test
    void constructorFanNull() {
        assertThrows(NullPointerException.class, () -> new Oven(heatingModuleMock, null));
    }

    @Test
    void runProgramBakingProgramNull() {
        assertThrows(NullPointerException.class, () -> oven.runProgram(null));
    }

    @Test
    void initOvenException() throws HeatingException {
        doThrow(HeatingException.class).when(heatingModuleMock).heater(any(HeatingSettings.class));
        BakingProgram programWithNonZeroStartingTemperature = BakingProgram.builder()
                .withCoolAtFinish(false)
                .withInitialTemp(1)
                .withStages(Collections.singletonList(standardStage))
                .build();

        assertThrows(OvenException.class, () -> oven.runProgram(programWithNonZeroStartingTemperature));
    }

    @Test
    void runStageOvenExceptionThermoCirculation() throws HeatingException {
        doThrow(HeatingException.class).when(heatingModuleMock).termalCircuit(any(HeatingSettings.class));
        BakingProgram programWithThermoCirculation = createProgram(HeatType.THERMO_CIRCULATION, false);

        assertThrows(OvenException.class, () -> oven.runProgram(programWithThermoCirculation));
    }

    @Test
    void runStageThermoCirculationCorrectCallOrder() throws HeatingException {
        BakingProgram programWithThermoCirculation = createProgram(HeatType.THERMO_CIRCULATION, false);

        oven.runProgram(programWithThermoCirculation);
        InOrder callOrder = inOrder(heatingModuleMock, fanMock);
        callOrder.verify(fanMock).on();
        callOrder.verify(heatingModuleMock).termalCircuit(any(HeatingSettings.class));
        callOrder.verify(fanMock).off();
    }

    @Test
    void isCoolingAtFinishNoThermalCirculation() {
        BakingProgram programWithCooling = BakingProgram.builder()
                .withCoolAtFinish(true)
                .withInitialTemp(0)
                .withStages(Collections.singletonList(standardStage))
                .build();

        oven.runProgram(programWithCooling);
        verify(fanMock).on();
    }

    @Test
    void isCoolingAtFinishWithThermalCirculation() {
        BakingProgram programWithThermoCirculation = createProgram(HeatType.THERMO_CIRCULATION, true);

        oven.runProgram(programWithThermoCirculation);
        InOrder callOrder = inOrder(fanMock);
        callOrder.verify(fanMock).on();
        callOrder.verify(fanMock).off();
        callOrder.verify(fanMock).on();
    }

    @Test
    void isCoolingAtFinishWhenNotNeeded() {
        oven.runProgram(standardBakingProgram);
        verify(fanMock, never()).on();
    }

    @Test
    void grillHeatingOvenException() throws HeatingException {
        BakingProgram programWithGrill = createProgram(HeatType.GRILL, true);

        doThrow(HeatingException.class).when(heatingModuleMock).grill(any(HeatingSettings.class));
        assertThrows(OvenException.class, () -> oven.runProgram(programWithGrill));
    }

    @Test
    void heaterHeatingOvenException() throws HeatingException {
        BakingProgram programWithHeater = createProgram(HeatType.HEATER, true);

        doThrow(HeatingException.class).when(heatingModuleMock).heater(any(HeatingSettings.class));
        assertThrows(OvenException.class, () -> oven.runProgram(programWithHeater));
    }

    @Test
    void correctOvenUsageTest() {
        try {
            oven.runProgram(standardBakingProgram);
        } catch (Exception anyException) {
            fail("This call should raise no exceptions at all!");
        }
    }

    @Test
    void correctOvenUsageMoreStages() {
        List<ProgramStage> stages = Arrays.asList(createStage(60, 180, HeatType.GRILL),
                createStage(120, 60, HeatType.THERMO_CIRCULATION),
                createStage(120, 30, HeatType.HEATER));
        BakingProgram programWithMoreStages = BakingProgram.builder()
                .withStages(stages)
                .withCoolAtFinish(true)
                .withInitialTemp(30)
                .build();
        try {
            oven.runProgram(programWithMoreStages);
        } catch (Exception anyException) {
            fail("This call should raise no exceptions at all!");
        }
    }



    private BakingProgram createProgram(HeatType heatType, boolean cooling) {
        ProgramStage stage = createStage(1, 1, heatType);
        return BakingProgram.builder()
                .withCoolAtFinish(cooling)
                .withInitialTemp(0)
                .withStages(Collections.singletonList(stage))
                .build();
    }

    private ProgramStage createStage(int time, int temp, HeatType heatType) {
        return ProgramStage.builder()
                .withStageTime(time)
                .withTargetTemp(temp)
                .withHeat(heatType)
                .build();
    }
}
