
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import static java.lang.System.out;

public class CHIP8 extends Application {

    static int PC_OFFSET = 0x200;

    int SCREEN_WIDTH = 64;
    int SCREEN_HEIGHT = 32;

    static Random random = new Random();

    byte[] memory;
    byte[] keyInputs;
    byte[] displayBuffer;
    byte[] gpio; // Registers
    byte soundTimer; // Timer registers
    byte delayTimer; // Timer registers
    int index; // 16-bit index register
    int pc; // Program counter
    int opcode;

    int[] fonts = new int[]{0xF0, 0x90, 0x90, 0x90, 0xF0, // 0
            0x20, 0x60, 0x20, 0x20, 0x70, // 1
            0xF0, 0x10, 0xF0, 0x80, 0xF0, // 2
            0xF0, 0x10, 0xF0, 0x10, 0xF0, // 3
            0x90, 0x90, 0xF0, 0x10, 0x10, // 4
            0xF0, 0x80, 0xF0, 0x10, 0xF0, // 5
            0xF0, 0x80, 0xF0, 0x90, 0xF0, // 6
            0xF0, 0x10, 0x20, 0x40, 0x40, // 7
            0xF0, 0x90, 0xF0, 0x90, 0xF0, // 8
            0xF0, 0x90, 0xF0, 0x10, 0xF0, // 9
            0xF0, 0x90, 0xF0, 0x90, 0x90, // A
            0xE0, 0x90, 0xE0, 0x90, 0xE0, // B
            0xF0, 0x80, 0x80, 0x80, 0xF0, // C
            0xE0, 0x90, 0x90, 0x90, 0xE0, // D
            0xF0, 0x80, 0xF0, 0x80, 0xF0, // E
            0xF0, 0x80, 0xF0, 0x80, 0x80  // F
    };

    boolean shouldDraw;

    Deque<Integer> stack; // Stack

    public void init() {
        this.memory = new byte[4096];
        this.displayBuffer = new byte[SCREEN_WIDTH * SCREEN_HEIGHT];
        this.gpio = new byte[16];
        this.keyInputs = new byte[16];

        this.pc = PC_OFFSET;

        this.soundTimer = 0;
        this.delayTimer = 0;
        this.index = 0;
        this.opcode = 0;

        this.stack = new ArrayDeque<>();

        this.shouldDraw = false;

        // Load fonts
        for (int i = 0; i < this.fonts.length; i++) {
            this.memory[i] = (byte) this.fonts[i];
        }

        Parameters params = getParameters();
        List<String> paramsList = params.getRaw();
        String romPath = paramsList.size() > 0 ? paramsList.get(0) : null;

        if (romPath != null) {
            this.loadRom(romPath);
        } else {
            out.println("No ROM was provided!");
        }
    }

    void loadRom(String romPath) {
        out.printf("Loading %s...%n", romPath);
        File romFile = new File(romPath);

        try (FileInputStream stream = new FileInputStream(romFile)) {
            int i = 0;
            byte b;
            while ((b = (byte) stream.read()) != -1) {
                this.memory[i + PC_OFFSET] = b;
                i++;
            }
        } catch (IOException e) {
            out.println("Failed to read ROM.");
        }

    }

    int x;
    int y;

    byte kk;

    void cycle() {

        this.opcode = 0xffff & (this.memory[this.pc] << 8) | this.memory[this.pc + 1];
        this.x = (opcode & 0x0f00) >> 8;
        this.y = (opcode & 0x00f0) >> 4;
        this.kk = (byte) (this.pc & 0x00ff);

        this.pc += 2;

        executeOp();


        if (delayTimer > 0) {
            this.delayTimer -= 1;
        }
        if (soundTimer > 0) {
            this.soundTimer -= 1;

            if (this.soundTimer == 0) {
                //TODO: play sound
            }
        }
    }

    void missingInstruction() {
        out.printf("Unknown instruction 0x%x\n", opcode);
    }

    void executeOp() {
        int parsed = this.opcode & 0xf000;
        out.printf("Executing %x...\n", this.opcode);

        switch (parsed) {
            case 0x0000:
                switch (opcode & 0x000f) {
                    case 0xE -> this._00EE();
                    // case 0x0 -> this._00E0();
                    default -> this.missingInstruction();
                }
                break;
            case 0x1000:
                this._1nnn();
            /*
            case 0x2000:
                this._2nnn();
            case 0x3000:
                this._3xkk();
            case 0x4000:
                this._4xkk();
            case 0x5000:
                this._5xy0();
             */
            case 0x6000:
                this._6xkk();
            case 0x7000:
                this._7xkk();
            /*
            case 0x8000:
                switch (opcode & 0x000f) {
                    case 0x0 -> this._8xy0();
                    case 0x1 -> this._8xy1();
                    case 0x2 -> this._8xy2();
                    case 0x3 -> this._8xy3();
                    case 0x4 -> this._8xy4();
                    case 0x5 -> this._8xy5();
                    case 0x6 -> this._8xy6();
                    case 0x7 -> this._8xy7();
                    case 0xE -> this._8xyE();
                    default -> this.missingInstruction();
                }
                break;
            */
            case 0xa000:
                this._Annn();
                break;
            /*
            case 0xb000:
                this._Bnnn();
                break;
            case 0xc000:
                this._Cxkk();
                break;
             */
            case 0xd000:
                this._Dxyn();
                break;
            // TODO
            default:
                this.missingInstruction();
        }
    }

    void _00E0() {
        this.displayBuffer = new byte[SCREEN_HEIGHT * SCREEN_WIDTH];
    }

    void _00EE() {
        this.pc = stack.size() - 1;
        this.pc = Math.min(0, this.pc);
        this.stack.pop();
    }

    void _1nnn() {
        this.pc = this.opcode & 0x0fff;
    }

    void _2nnn() {
        this.stack.push(this.pc);
        this._1nnn();
    }

    void _3xkk() {
        if (this.gpio[x] == kk) {
            this.pc += 2;
        }
    }

    void _4xkk() {
        if (this.gpio[x] != kk) {
            this.pc += 2;
        }
    }

    void _5xy0() {
        if (this.gpio[x] == this.gpio[y]) {
            this.pc += 2;
        }
    }

    void _6xkk() {
        this.gpio[x] = kk;
    }

    void _7xkk() {
        this.gpio[x] += kk;
    }

    void _8xy0() {
        this.gpio[x] = this.gpio[y];
    }

    void _8xy1() {
        this.gpio[x] = (byte) (this.gpio[x] | this.gpio[y]);
    }

    void _8xy2() {
        this.gpio[x] = (byte) (this.gpio[x] & this.gpio[y]);
    }


    void _8xy3() {
        this.gpio[x] = (byte) (this.gpio[x] ^ this.gpio[y]);
    }

    void _8xy4() {
        byte c = (byte) (x + y > 255 ? 1 : 0);

        this.gpio[x] = (byte) (this.gpio[x] - this.gpio[y]);
        this.gpio[0xf] = c;
    }

    void _8xy5() {
        byte VF = (byte) (this.gpio[x] > this.gpio[y] ? 1 : 0);

        this.gpio[x] = (byte) (this.gpio[x] - this.gpio[y]);
        this.gpio[0xf] = VF;
    }

    void _8xy6() {
        int VF = this.gpio[x] & 0x000f;
        this.gpio[x] /= 2;
        this.gpio[0xf] = (byte) VF;
    }


    void _8xy7() {
        int VF = this.gpio[y] > this.gpio[x] ? 1 : 0;

        this.gpio[x] = (byte) (this.gpio[y] - this.gpio[x]);
        this.gpio[0xf] = (byte) VF;
    }

    void _8xyE() {
        int VF = (this.gpio[x] & 0xf000);

        this.gpio[x] *= 2;
        this.gpio[0xf] = (byte) VF;
    }

    void _9xy0() {
        if (this.gpio[x] != this.gpio[y]) {
            this.pc += 2;
        }
    }

    void _Annn() {
        this.index = this.opcode & 0x0fff;
    }

    void _Bnnn() {
        this.pc = this.opcode & 0x0fff + this.gpio[0x0];
    }

    void _Cxkk() {
        this.gpio[x] = (byte) random.nextInt(0, 256);
        this.gpio[x] += this.kk;
    }

    void _Dxyn() {
        int n = this.opcode & 0x000f;

        int VX = this.gpio[x];
        int VY = this.gpio[y];

        for (int i = 0; i < n; i++) {
            byte currRow = this.memory[this.index + i];

            for (int j = 0; j < 8; j++) {
                byte mask = (byte) (0x01 << (8 - j));
                byte currPixel = (byte) ((currRow & mask) >> (8 - j));

                int loc = x + j + (VY * SCREEN_WIDTH);

                this.displayBuffer[loc] ^= currPixel;
                if (this.displayBuffer[loc] == 0) {
                    this.gpio[0xf] = 1;
                } else {
                    this.gpio[0xf] = 0;
                }

                VX++;

                if (VX >= SCREEN_WIDTH) {
                    break;
                }
            }
            VY++;

            if (VY >= SCREEN_HEIGHT) {
                break;
            }
        }

        this.shouldDraw = true;
    }


    // RENDERING

    long lastUpdateTime;

    final long INTERVAL = 250_000_000;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Group root = new Group();
        Canvas canvas = new Canvas(SCREEN_WIDTH, SCREEN_HEIGHT);

        root.getChildren().addAll(canvas);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Create a timer
        AnimationTimer timer = new AnimationTimer() {
            // This method called by FX, parameter is the current time
            public void handle(long now) {
                long elapsedNanos = now - lastUpdateTime;
                if (elapsedNanos > INTERVAL) {
                    cycle();
                    renderDisplay(gc);
                    lastUpdateTime = now;
                }
            }
        };

        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Simulation");
        primaryStage.show();

        timer.start();  // Start simulation
    }

    void renderDisplay(GraphicsContext gc) {
        gc.clearRect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        for (int row = 0; row < SCREEN_HEIGHT; row++) {
            for (int col = 0; col < SCREEN_HEIGHT; col++) {
                if (this.displayBuffer[SCREEN_WIDTH * row + col] != 0x0) {
                    gc.setFill(Color.web("#000000"));
                    gc.fillRect(row, col, row + 1, col + 1);
                    gc.scale(2, 2);
                }
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}