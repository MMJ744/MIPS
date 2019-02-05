package utils;

import java.awt.geom.Point2D.Double;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import objects.Entity;
import renderer.Renderer;
import utils.enums.Direction;

public class ImageTester extends Application {

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage stage) {
    final Canvas canvas = new Canvas(1920, 1080);
    final GraphicsContext gc = canvas.getGraphicsContext2D();

    ResourceLoader resourceLoader = new ResourceLoader("src/test/resources/");

    Map map = resourceLoader.getMap();
    Renderer r = new Renderer(gc, 1920, 1080, resourceLoader.getMapTiles());



    Entity mip = new Entity(true, 1, resourceLoader);
    mip.setPacMan(true);
    mip.setLocation(new Double(1, 2));

    Entity ghoul = new Entity(false, 4, resourceLoader);
    ghoul.setLocation(new Double(6.5, 3));
    ghoul.setPacMan(false);
    ghoul.setDirection(Direction.RIGHT);

    Entity[] entities = new Entity[]{mip, ghoul};

    Image background = resourceLoader.getBackground();
    gc.drawImage(background, 0, 0, 1920, 1080);

    r.render(map, entities);
    stage.setScene(new Scene(new Group(canvas)));
    stage.show();

  }
}
