/*
 * The MIT License (MIT)
 *
 * FXGL - JavaFX Game Library
 *
 * Copyright (c) 2015 AlmasB (almaslvl@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.almasb.geowars;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.ServiceType;
import com.almasb.fxgl.asset.Texture;
import com.almasb.fxgl.entity.EntityView;
import com.almasb.fxgl.entity.control.Control;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.entity.EntityType;
import com.almasb.fxgl.entity.control.ProjectileControl;
import com.almasb.fxgl.input.Input;
import com.almasb.fxgl.input.UserAction;
import com.almasb.fxgl.physics.CollisionHandler;
import com.almasb.fxgl.physics.PhysicsWorld;
import com.almasb.fxgl.settings.GameSettings;
import com.almasb.fxgl.time.LocalTimer;
import com.almasb.fxgl.time.FXGLMasterTimer;
import com.almasb.fxgl.app.ApplicationMode;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.HorizontalDirection;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * @author Almas Baimagambetov (AlmasB) (almaslvl@gmail.com)
 */
public class GeoWarsApp extends GameApplication {

    private enum Type implements EntityType {
        PLAYER, WANDERER, SEEKER, BULLET,
        SHOCKWAVE,
        EXPLOSION
    }

    private Entity player;

    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setTitle("FXGL Geometry Wars");
        settings.setVersion("0.2dev");
        settings.setFullScreen(false);
        settings.setIntroEnabled(false);
        settings.setMenuEnabled(false);
        settings.setShowFPS(true);
        settings.setApplicationMode(ApplicationMode.DEVELOPER);
    }

    @Override
    protected void initInput() {
        Input input = getInput();

        input.addAction(new UserAction("Move Left") {
            @Override
            protected void onAction() {
                player.translate(-5, 0);
            }
        }, KeyCode.A);

        input.addAction(new UserAction("Move Right") {
            @Override
            protected void onAction() {
                player.translate(5, 0);
            }
        }, KeyCode.D);

        input.addAction(new UserAction("Move Up") {
            @Override
            protected void onAction() {
                player.translate(0, -5);
            }
        }, KeyCode.W);

        input.addAction(new UserAction("Move Down") {
            @Override
            protected void onAction() {
                player.translate(0, 5);
            }
        }, KeyCode.S);

        input.addAction(new UserAction("Shoot") {
            private LocalTimer timer = getService(ServiceType.LOCAL_TIMER);

            @Override
            protected void onAction() {
                if (timer.elapsed(Duration.seconds(0.33))) {
                    shoot();
                    timer.capture();
                }
            }
        }, MouseButton.PRIMARY);

        input.addAction(new UserAction("Shockwave") {
            @Override
            protected void onActionBegin() {
                spawnShockwave();
            }
        }, KeyCode.F);
    }

    private Texture textureExplosion;

    @Override
    protected void initAssets() {
        textureExplosion = getAssetLoader().loadTexture("explosion.png");
        int h = 1536 / 6;
        Texture textureCombined = textureExplosion.subTexture(new Rectangle2D(0, 0, 2048, h));

        for (int i = 1; i < 6; i++) {
            textureCombined = textureCombined
                    .superTexture(textureExplosion.subTexture(new Rectangle2D(0, h*i, 2048, h)), HorizontalDirection.RIGHT);
        }

        textureExplosion = textureCombined;
    }

    @Override
    protected void initGame() {
        getAudioPlayer().setGlobalSoundVolume(0.1);
        getAudioPlayer().setGlobalMusicVolume(0.1);

        initBackground();
        initPlayer();

        getMasterTimer().runAtInterval(this::spawnWanderer, Duration.seconds(2));
        getMasterTimer().runAtInterval(this::spawnSeeker, Duration.seconds(5));
    }

    @Override
    protected void initPhysics() {
        PhysicsWorld physics = getPhysicsWorld();

        CollisionHandler bulletEnemy = new CollisionHandler(Type.BULLET, Type.WANDERER) {
            @Override
            protected void onCollisionBegin(Entity a, Entity b) {
                spawnExplosion(b.getCenter());

                a.removeFromWorld();
                b.removeFromWorld();
                addScoreKill();
            }
        };

        physics.addCollisionHandler(bulletEnemy);
        physics.addCollisionHandler(bulletEnemy.copyFor(Type.BULLET, Type.SEEKER));

        CollisionHandler playerEnemy = new CollisionHandler(Type.PLAYER, Type.WANDERER) {
            @Override
            protected void onCollisionBegin(Entity a, Entity b) {
                a.setPosition(getRandomPoint());
                b.removeFromWorld();
                deductScoreDeath();
            }
        };

        physics.addCollisionHandler(playerEnemy);
        physics.addCollisionHandler(playerEnemy.copyFor(Type.PLAYER, Type.SEEKER));

        CollisionHandler shockEnemy = new CollisionHandler(Type.SHOCKWAVE, Type.SEEKER) {
            @Override
            protected void onCollisionBegin(Entity a, Entity b) {
                b.translate(b.getPosition().
                        subtract(player.getPosition()).
                        normalize()
                        .multiply(100));
            }
        };

        physics.addCollisionHandler(shockEnemy);
        physics.addCollisionHandler(shockEnemy.copyFor(Type.SHOCKWAVE, Type.WANDERER));
    }

    private IntegerProperty score = new SimpleIntegerProperty(0);

    @Override
    protected void initUI() {
        Text scoreText = new Text();
        scoreText.setFont(Font.font(18));
        scoreText.setTranslateX(1100);
        scoreText.setTranslateY(50);
        scoreText.textProperty().bind(score.asString("Score: %d"));

        getGameScene().addUINode(scoreText);
    }

    @Override
    protected void onUpdate() {
        player.getComponentUnsafe(OldPositionComponent.class)
                .setValue(player.getPosition());

        cleanOffscreenBullets();
    }

    private void initBackground() {
        Entity bg = Entity.noType();
        bg.setSceneView(getAssetLoader().loadTexture("background.png"));

        getGameWorld().addEntity(bg);
    }

    private void initPlayer() {
        player = new Entity(Type.PLAYER);
        player.setPosition(getWidth() / 2, getHeight() / 2);
        player.setCollidable(true);

        OldPositionComponent oldPosition = new OldPositionComponent();
        oldPosition.valueProperty().addListener((obs, old, newPos) -> {
            player.rotateToVector(newPos.subtract(old));
        });

        player.addComponent(oldPosition);

        Polygon triangle = new Polygon(0, 0, 40, 20, 0, 40);
        triangle.setStroke(Color.BLUE);
        triangle.setStrokeWidth(3);

        player.setSceneView(triangle);

        getGameWorld().addEntity(player);
    }

    private void spawnWanderer() {
        Entity wanderer = new Entity(Type.WANDERER);
        wanderer.setPosition(50, 50);
        wanderer.addControl(new WandererControl());
        wanderer.setCollidable(true);

        Rectangle rect = new Rectangle(40, 40);
        rect.setArcHeight(15);
        rect.setArcWidth(15);
        rect.setFill(Color.GREENYELLOW);

        wanderer.setSceneView(rect);

        getGameWorld().addEntity(wanderer);
    }

    private void spawnSeeker() {
        Entity seeker = new Entity(Type.SEEKER);
        seeker.setPosition(50, 50);
        seeker.addControl(new SeekerControl());
        seeker.setCollidable(true);

        Circle circle = new Circle(20);
        circle.setFill(Color.DARKRED);

        seeker.setSceneView(circle);

        getGameWorld().addEntity(seeker);
    }

    private void spawnShockwave() {
        Entity shock = new Entity(Type.SHOCKWAVE);
        shock.setPosition(player.getCenter());

        Circle circle = new Circle(75);
        circle.setFill(null);
        circle.setStroke(Color.BLUEVIOLET);

        shock.setSceneView(circle);
        shock.xProperty().bind(player.xProperty().subtract(55));
        shock.yProperty().bind(player.yProperty().subtract(55));
        shock.setExpireTime(Duration.seconds(5));
        shock.setCollidable(true);

        getGameWorld().addEntity(shock);
    }

    private void spawnExplosion(Point2D point) {
        Entity explosion = new Entity(Type.EXPLOSION);
        explosion.setPosition(point.subtract(40, 40));

        Texture animation = textureExplosion.toStaticAnimatedTexture(48, Duration.seconds(2));
        animation.setFitWidth(80);
        animation.setFitHeight(80);
        explosion.setSceneView(animation);
        explosion.setExpireTime(Duration.seconds(2));

        getGameWorld().addEntity(explosion);
    }

    private void shoot() {
        Entity bullet = new Entity(Type.BULLET);
        bullet.setPosition(player.getCenter());
        bullet.addControl(new ProjectileControl(getVectorToCursor(bullet.getPosition()), 10));
        bullet.setCollidable(true);

        Rectangle rect = new Rectangle(20, 1);
        rect.setFill(Color.RED);

        bullet.setSceneView(rect);

        getGameWorld().addEntity(bullet);
    }

    private void cleanOffscreenBullets() {
        getGameWorld().getEntities(Type.BULLET)
                .stream()
                .filter(b -> b.isOutside(0, 0, getWidth(), getHeight()))
                .forEach(Entity::removeFromWorld);
    }

    private Point2D getVectorToCursor(Point2D point) {
        return getInput().getMouse()
                .getGameXY()
                .subtract(point);
    }

    private Point2D getRandomPoint() {
        return new Point2D(Math.random() * getWidth(), Math.random() * getHeight());
    }

    private void addScoreKill() {
        score.set(score.get() + 100);
    }

    private void deductScoreDeath() {
        score.set(score.get() - 1000);
    }

    private class WandererControl implements Control {
        private LocalTimer timer = getService(ServiceType.LOCAL_TIMER);
        private Point2D velocity = Point2D.ZERO;

        @Override
        public void onUpdate(Entity entity) {
            if (timer.elapsed(Duration.seconds(4))) {
                velocity = getRandomPoint().subtract(entity.getPosition())
                        .multiply(FXGLMasterTimer.tpfSeconds() / 4);
                timer.capture();
            }

            entity.translate(velocity);
        }
    }

    private class SeekerControl implements Control {
        private LocalTimer timer = getService(ServiceType.LOCAL_TIMER);
        private Point2D velocity = Point2D.ZERO;

        @Override
        public void onUpdate(Entity entity) {
            if (timer.elapsed(Duration.seconds(2))) {
                velocity = player.getPosition().subtract(entity.getPosition())
                        .normalize()
                        .multiply(5);
                timer.capture();
            }

            entity.translate(velocity);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}