package cam72cam.mod.config;

import cam72cam.mod.gui.*;
import cam72cam.mod.util.Hand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConfigGui implements IScreen {
    private final ConfigGui parent;
    private final ConfigFile.PropertyClass pc;
    List<Function<IScreenBuilder, Object>> widgets = new ArrayList<>();
    private IScreenBuilder screen;


    public ConfigGui(Class<?> ...types) {
        parent = null;
        pc = null;
        int i = -1;
        for (Class<?> type : types) {
            int finalI = i;
            ConfigFile.ConfigInstance ci = new ConfigFile.ConfigInstance(type);
            ci.read();
            widgets.add(screen -> new Button(screen, 0 - 100,  finalI * 20, 200, 20, type.getSimpleName()) {
                @Override
                public void onClick(Hand hand) {
                    Minecraft.getMinecraft().displayGuiScreen(new ScreenBuilder(new ConfigGui(ConfigGui.this, ci.pc, ci)));
                }
            });
            i++;
        }
    }

    public ConfigGui(ConfigGui parent, ConfigFile.PropertyClass pc, ConfigFile.ConfigInstance ci) {
        this.parent = parent;
        this.pc = pc;

        List<Consumer<Integer>> pageEvent = new ArrayList<>();
        BiConsumer<Integer, Consumer<Boolean>> onPage = (i, fn) -> {
            pageEvent.add((page) -> fn.accept(Math.floor(i / 8.0) == page));
            fn.accept(Math.floor(i / 8.0) == 0);
        };


        int i = -1;
        for (ConfigFile.Property property : pc.properties) {
            int finalI = i+1;
            int offsetI = ((i+1) % 8)-1;
            if (property instanceof ConfigFile.PropertyClass) {
                widgets.add(screen -> {
                    Button btn = new Button(screen, 0 - 100, offsetI * 20, 200, 20, property.getName()) {
                        @Override
                        public void onClick(Hand hand) {
                            Minecraft.getMinecraft().displayGuiScreen(new ScreenBuilder(new ConfigGui(ConfigGui.this, (ConfigFile.PropertyClass) property, ci)));
                        }
                    };
                    onPage.accept(finalI, btn::setVisible);
                    return btn;
                });
            }
            if (property instanceof ConfigFile.PropertyField) {
                ConfigFile.PropertyField prop = (ConfigFile.PropertyField) property;
                try {
                    if (prop.field.get(null) instanceof String) {
                        String text = (String) prop.field.get(null);
                        widgets.add(screen -> {
                            TextField tf = new TextField(screen, 1, offsetI * 20 + 1, 196, 18);
                            tf.setText(text);
                            tf.setValidator(str -> {
                                try {
                                    prop.field.set(null, str);
                                    ci.write();
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                                return true;
                            });
                            onPage.accept(finalI, tf::setVisible);
                            return tf;
                        });
                    } else if (prop.field.get(null) instanceof Boolean) {
                        Boolean val = prop.field.getBoolean(null);
                        widgets.add(screen -> {
                            Button btn = new Button(screen, -1, offsetI * 20, 200, 20, val.toString()) {
                                @Override
                                public void onClick(Hand hand) {
                                    Boolean value = !Boolean.parseBoolean(this.getText());
                                    this.setText(value.toString());
                                    try {
                                        prop.field.setBoolean(null, value);
                                        ci.write();
                                    } catch (IllegalAccessException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            onPage.accept(finalI, btn::setVisible);
                            return btn;
                        });
                    } else if (prop.field.getType().isEnum()) {
                        Enum val = (Enum) prop.field.get(null);
                        Enum[] arry = (Enum[]) prop.field.getType().getEnumConstants();
                        widgets.add(screen -> {
                            Button btn = new Button(screen, -1, offsetI * 20, 200, 20, val.toString()) {
                                Enum curr = val;
                                @Override
                                public void onClick(Hand hand) {
                                    curr = arry[(curr.ordinal()+1) % arry.length];
                                    this.setText(curr.toString());
                                    try {
                                        prop.field.set(null, curr);
                                        ci.write();
                                    } catch (IllegalAccessException e) {
                                        e.printStackTrace();
                                    }
                                }
                            };
                            onPage.accept(finalI, btn::setVisible);
                            return btn;
                        });
                    } else if (prop.field.get(null) instanceof Double) {
                        Double val = (Double) prop.field.get(null);
                        widgets.add(screen -> {
                            TextField tf = new TextField(screen, 1, offsetI * 20 + 1, 196, 18);
                            tf.setText(val.toString());
                            tf.setValidator(str -> {
                                try {
                                    prop.field.set(null, Double.parseDouble(str));
                                    ci.write();
                                } catch (IllegalAccessException | NumberFormatException e) {
                                    return false;
                                }
                                return true;
                            });
                            onPage.accept(finalI, tf::setVisible);
                            return tf;
                        });
                    } else if (prop.field.get(null) instanceof Float) {
                        Float val = (Float) prop.field.get(null);
                        widgets.add(screen -> {
                            TextField tf = new TextField(screen, 1, offsetI * 20 + 1, 196, 18);
                            tf.setText(val.toString());
                            tf.setValidator(str -> {
                                try {
                                    prop.field.set(null, Float.parseFloat(str));
                                    ci.write();
                                } catch (IllegalAccessException | NumberFormatException e) {
                                    return false;
                                }
                                return true;
                            });
                            onPage.accept(finalI, tf::setVisible);
                            return tf;
                        });
                    } else if (prop.field.get(null) instanceof Integer) {
                        Integer val = (Integer) prop.field.get(null);
                        widgets.add(screen -> {
                            TextField tf = new TextField(screen, 1, offsetI * 20 + 1, 196, 18);
                            tf.setText(val.toString());
                            tf.setValidator(str -> {
                                try {
                                    prop.field.set(null, Integer.parseInt(str));
                                    ci.write();
                                } catch (IllegalAccessException | NumberFormatException e) {
                                    return false;
                                }
                                return true;
                            });
                            onPage.accept(finalI, tf::setVisible);
                            return tf;
                        });
                    } else {
                        //continue;
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
                widgets.add(screen -> {
                    Button btn = new Button(screen, -200, offsetI * 20, 200, 20, property.getName()) {
                        @Override
                        public void onClick(Hand hand) {

                        }
                    };
                    btn.setEnabled(false);
                    onPage.accept(finalI, btn::setVisible);
                    return btn;
                });
            }

            i++;
        }

        int pages = (int) Math.ceil((i+1) / 8.0f);
        widgets.add(screen -> new Button(screen, 0 - 100,  150, 200, 20, "Page: 1/" + pages) {
            int i = 0;

            @Override
            public void onClick(Hand hand) {
                i++;
                if (i >= pages) {
                    i = 0;
                }
                this.setText(String.format("Page: %s/%s", i+1, pages));
                pageEvent.forEach(x -> x.accept(i));
            }
        });
    }

    @Override
    public void init(IScreenBuilder screen) {
        this.screen = screen;
        for (Function<IScreenBuilder, Object> widgetsup : widgets) {
            widgetsup.apply(screen);
        }
    }

    @Override
    public void onEnterKey(IScreenBuilder builder) {
        builder.close();
    }

    @Override
    public void onClose() {
        if (parent != null) {
            parent.show();
        }
    }

    private void show() {
        screen.show();
    }

    @Override
    public void draw(IScreenBuilder builder) {
        ((GuiScreen)builder).drawBackground(0);

        String name = "";
        ConfigGui iter = this;
        while (iter != null && iter.pc != null) {
            name = " > " + iter.pc.getName() + name;
            iter = iter.parent;
        }
        name = "Config" + name;



        builder.drawCenteredString(name, 0, -50, 0xFFFFFF);
    }
}
