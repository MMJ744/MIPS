package renderer;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import objects.Entity;
import objects.Pellet;
import utils.Map;
import utils.Point;
import utils.ResourceLoader;
import utils.enums.MapElement;
import utils.enums.RenderingMode;

public class Renderer {

  private static final double MAP_BORDER = 10;
  private final GraphicsContext gc;
  private final long secondInNanoseconds = (long) Math.pow(10, 9);
  private ResourceLoader r;
  private int xResolution;
  private int yResolution;
  private Point2D.Double mapRenderingCorner;
  private ArrayList<Image> mapTiles;
  private Image background;
  private BufferedImage palette;
  private double tileSizeX;
  private double tileSizeY;
  private int clientID;
  private Font geoSmall;
  private Font geoLarge;
  private long lastFrame;
  private int fps = 0;
  private int frameCounter = 0;
  private long timeSum;

  private ArrayList<Point2D.Double> traversalOrder = new ArrayList<>();

  /**
   * @param _gc Graphics context to render the game onto
   * @param _xResolution Game x resolution
   * @param _yResolution Game y resolution
   * @param r Asset loader
   */
  public Renderer(GraphicsContext _gc, int _xResolution, int _yResolution, ResourceLoader r) {
    this.r = r;
    this.gc = _gc;
    this.xResolution = _xResolution;
    this.yResolution = _yResolution;
    this.background = r.getBackground();
    this.palette = r.getBackgroundPalette();

    this.init();
  }

  /**
   * initialises map array, map traversal order, map tiles and fonts
   */
  public void init() {
    Map map = r.getMap();

    final int ROW = map.getMaxX();
    final int COL = map.getMaxY();

    traversalOrder = new ArrayList<>();
    //find diagonal traversal order (map depth order traversal)
    for (int line = 1; line <= (ROW + COL - 1); line++) {
      int start_col = Math.max(0, line - ROW);

      int count = Math.min(line, Math.min(COL - start_col, ROW));

      // Print elements of this line
      for (int j = 0; j < count; j++) {
        int x = Math.min(ROW, line) - j - 1;
        int y = start_col + j;
        this.traversalOrder.add(new Double(x, y));
      }
    }

    this.mapTiles = r.getMapTiles();
    this.mapRenderingCorner = getMapRenderingCorner();
    tileSizeX = r.getMapTiles().get(0).getWidth();
    tileSizeY = r.getMapTiles().get(0).getHeight();

    // set fonts
    final double fontRatio = 0.07;
    try {
      this.geoLarge =
          Font.loadFont(
              new FileInputStream(new File("src/main/resources/font/Geo-Regular.ttf")),
              xResolution * fontRatio);
      this.geoSmall =
          Font.loadFont(
              new FileInputStream(new File("src/main/resources/font/Geo-Regular.ttf")),
              0.8 * xResolution * fontRatio);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  /**
   * @param map Game Map
   * @param entityArr Playable objects
   * @param now Current game time in nanoseconds
   * @param pellets Consumable objects
   */
  public void render(Map map, Entity[] entityArr, long now, HashMap<String, Pellet> pellets) {
    //clear screen
    gc.clearRect(0, 0, xResolution, yResolution);
    renderBackground(map);
    renderGameOnly(map, entityArr, now, pellets);
    renderHUD(entityArr);
    showFPS(now);
  }

  public void renderGameOnly(Map map, Entity[] entityArr, long now,
      HashMap<String, Pellet> pellets) {
    int[][] rawMap = map.raw();
    ArrayList<Entity> entities = new ArrayList<>(Arrays.asList(entityArr));
    // sort entities to get rendering order
    entities.sort(Comparator.comparingDouble(o -> o.getLocation().getX() + o.getLocation().getY()));

    int entityCounter = 0;
    Image currentSprite;
    Double rendCoord;
    Point spriteCoord = new Point(java.lang.Double.MAX_VALUE, java.lang.Double.MAX_VALUE);

    int x;
    int y;

    // Render floor first (floors will never be on a higher layer than anything apart form the background
    for (Double coord : traversalOrder) {
      x = (int) coord.getX();
      y = (int) coord.getY();

      if (MapElement.FLOOR.toInt() == rawMap[x][y]) {
        rendCoord =
            getIsoCoord(
                x,
                y,
                mapTiles.get(MapElement.FLOOR.toInt()).getHeight(),
                mapTiles.get(MapElement.FLOOR.toInt()).getWidth());
        gc.drawImage(mapTiles.get(MapElement.FLOOR.toInt()), rendCoord.x, rendCoord.y);
      }
    }

    // TODO refactor the way the translucent pellet is fetched
    Image translucentPellet = r.getTranslucentPellet().get(0);

    // Loop through grid in diagonal traversal to render walls and entities by depth
    for (Double coord : traversalOrder) {
      x = (int) coord.getX();
      y = (int) coord.getY();

      // render consumable objects on top
      Pellet currentPellet = pellets.get(x + "," + y);
      if (currentPellet != null && currentPellet.isActive()) {

        //TODO use better way of finding if client is mipsman
        boolean clientMipsman = isClientMipsman(entities);

        if (clientMipsman) {
          currentSprite = currentPellet.getImage().get(0);
        } else {
          currentSprite = translucentPellet;
        }

        //render pellet using either translucent or opaque sprite
        double x_ = currentPellet.getLocation().getX() - 0.5;
        double y_ = currentPellet.getLocation().getY() - 0.5;
        rendCoord = getIsoCoord(x_, y_, currentSprite.getHeight(), currentSprite.getWidth());
        gc.drawImage(currentSprite, rendCoord.getX(), rendCoord.getY());
      }

      currentSprite = mapTiles.get(rawMap[x][y]);
      rendCoord = getIsoCoord(x, y, currentSprite.getHeight(), currentSprite.getWidth());
      if (MapElement.FLOOR.toInt() == rawMap[x][y]) {
        continue;
      }

      // render wall (or any other non passable terrain)
      gc.drawImage(currentSprite, rendCoord.x, rendCoord.y);

      if (entityCounter < entities.size()) {
        spriteCoord = entities.get(entityCounter).getLocation();
      }

      // is the current entities depth the same or deeper than the wall just rendered?
      while (entityCounter < entities.size()
          && ((x + y) >= ((int) spriteCoord.getX() + (int) spriteCoord.getY()))
          && spriteCoord.getX() > x) {
        renderEntity(entities.get(entityCounter), now - lastFrame);
        entityCounter++;

        // point to the next entity
        if (entityCounter < entities.size()) {
          spriteCoord = entities.get(entityCounter).getLocation();
        }
      }
    }
  }

  private boolean isClientMipsman(ArrayList<Entity> entities) {
    boolean clientMipsman = false;

    // TODO refactor the way the render knows the client is MIPSman
    for (Entity e : entities) {
      if (e.isMipsman() && e.getClientId() == clientID) {
        clientMipsman = true;
        break;
      }
    }
    return clientMipsman;
  }

  public void renderCollisionAnimation(Entity newMipsMan){
    Image currentSprite = newMipsMan.getImage().get(newMipsMan.getCurrentFrame());
    final double renderAnimationTime = 0.75 * Math.pow(10,9);
    double startTime = System.nanoTime();
    final int frames = 22;
    double frameTime = renderAnimationTime/frames;
    int currentFrame = 0;
    while(System.nanoTime()-startTime < renderAnimationTime){
      gc.setFill(Color.BLACK);
      gc.fillRect(0,0,xResolution,yResolution);
      gc.setTextAlign(TextAlignment.CENTER);

      double x = newMipsMan.getLocation().getX() - 0.5;
      double y = newMipsMan.getLocation().getY() - 0.5;
      Point2D.Double rendCoord =
              getIsoCoord(x, y, currentSprite.getHeight(), currentSprite.getWidth());
      gc.drawImage(currentSprite, rendCoord.getX(), rendCoord.getY());
      gc.fillText("MIPS CAPTURED",xResolution/2,yResolution*0.7);

      try {
        Thread.sleep(3);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }


  }


  /**
   * @param now current time in nanoseconds
   */
  private void showFPS(long now) {
    long timeElapsed = now - lastFrame;
    lastFrame = now;
    gc.setTextAlign(TextAlignment.CENTER);
    if (timeSum > secondInNanoseconds) {
      fps = frameCounter / (int) (timeSum / secondInNanoseconds);
      gc.fillText("FPS:" + fps, xResolution / 2, yResolution - 100);
      timeSum = 0;
      frameCounter = 0;
    } else {
      gc.fillText("FPS:" + fps, xResolution / 2, yResolution - 100);
      timeSum += timeElapsed;
      frameCounter++;
    }
  }

  /**
   * allows renderer to show a marker on who is the client's entity
   *
   * @param _id the ID of the entity which the client controls
   */
  public void setClientID(int _id) {
    this.clientID = _id;
  }

  /**
   * render the background image and pyramid under game map
   *
   * @param map game map
   */
  private void renderBackground(Map map) {
    // render backing image
    gc.drawImage(background, 0, 0, xResolution, yResolution);

    // Render map base
    Point2D.Double tmpCoord = getIsoCoord(0, 0, tileSizeY, tileSizeX);
    Point2D.Double topLeft =
        new Double(tmpCoord.getX() + 0.5 * tileSizeX, tmpCoord.getY() - 0.5 * MAP_BORDER);

    tmpCoord = getIsoCoord(map.getMaxX(), 0, tileSizeY, tileSizeX);
    Point2D.Double topRight =
        new Double(tmpCoord.getX() + MAP_BORDER + tileSizeX, tmpCoord.getY() + 0.5 * tileSizeY);

    tmpCoord = getIsoCoord(0, map.getMaxY(), tileSizeY, tileSizeX);
    Point2D.Double bottomLeft =
        new Double(tmpCoord.getX() - 0.5 * MAP_BORDER, tmpCoord.getY() + 0.5 * tileSizeY);

    tmpCoord = getIsoCoord(map.getMaxX(), map.getMaxY(), tileSizeY, tileSizeX);
    Point2D.Double bottomRight =
        new Double(
            tmpCoord.getX() + 0.5 * tileSizeX, tmpCoord.getY() + 0.5 * MAP_BORDER + tileSizeY);

    //get first colour from palette (lightest tone)
    setFillColour(palette.getRGB(0, 0));
    gc.fillPolygon(
        new double[]{topLeft.getX(), topRight.getX(), bottomRight.getX(), bottomLeft.getX()},
        new double[]{topLeft.getY(), topRight.getY(), bottomRight.getY(), bottomLeft.getY()},
        4);

    // Render Pyramid underside

    double yChange = topRight.getX() - bottomRight.getX();

    double percentageXRes = 0.04;
    double ratio = ((percentageXRes * xResolution) / yChange) * (map.getMaxY() / (double) 20);

    double x =
        getIsoCoord(map.getMaxX() / (double) 2, map.getMaxY() / (double) 2, tileSizeY, tileSizeX)
            .getX();
    double y = bottomRight.getY() + yChange * ratio;

    Point2D.Double pyramidVertex = new Point2D.Double(x, y);

    //get third colour from palette (darkest tone)
    setFillColour(palette.getRGB(2, 0));
    gc.fillPolygon(
        new double[]{topRight.getX(), bottomRight.getX(), pyramidVertex.getX()},
        new double[]{topRight.getY(), bottomRight.getY(), pyramidVertex.getY()},
        3);

    //get second colour from palette (medium tone)
    setFillColour(palette.getRGB(1, 0));
    gc.fillPolygon(
        new double[]{bottomLeft.getX(), bottomRight.getX(), pyramidVertex.getX()},
        new double[]{bottomLeft.getY(), bottomRight.getY(), pyramidVertex.getY()},
        3);

    // Draw black outline
    gc.setStroke(Color.BLACK);
    gc.strokePolygon(
        new double[]{bottomLeft.getX(), bottomRight.getX(), pyramidVertex.getX()},
        new double[]{bottomLeft.getY(), bottomRight.getY(), pyramidVertex.getY()},
        3);

    gc.strokePolygon(
        new double[]{topRight.getX(), bottomRight.getX(), pyramidVertex.getX()},
        new double[]{topRight.getY(), bottomRight.getY(), pyramidVertex.getY()},
        3);

    gc.strokePolygon(
        new double[]{topLeft.getX(), topRight.getX(), bottomRight.getX(), bottomLeft.getX()},
        new double[]{topLeft.getY(), topRight.getY(), bottomRight.getY(), bottomLeft.getY()},
        4);
  }

  /**
   * @param x cartesian x coordinate
   * @param y cartesian Y coordinate
   * @param spriteHeight vertical offset
   */
  private Point2D.Double getIsoCoord(double x, double y, double spriteHeight, double spriteWidth) {
    double isoX =
        mapRenderingCorner.getX()
            - (y - x) * (this.tileSizeX / (double) 2)
            + ((tileSizeX - spriteWidth) / 2);
    double isoY =
        mapRenderingCorner.getY()
            + (y + x) * (this.tileSizeY / (double) 2)
            + (tileSizeY - spriteHeight);
    return new Point2D.Double(isoX, isoY);
  }

  /**
   * @param e entitiy to render
   * @param timeElapsed time since last frame to decide whether to move to next animation frame
   */
  private void renderEntity(Entity e, long timeElapsed) {
    // choose correct animation
    ArrayList<Image> currentSprites = e.getImage();
    if (secondInNanoseconds / e.getAnimationSpeed() < e.getTimeSinceLastFrame()) {
      e.setTimeSinceLastFrame(0);
      e.nextFrame();
    } else {
      e.setTimeSinceLastFrame(e.getTimeSinceLastFrame() + timeElapsed);
    }
    Image currentSprite = currentSprites.get(e.getCurrentFrame());

    double x = e.getLocation().getX() - 0.5;
    double y = e.getLocation().getY() - 0.5;
    Point2D.Double rendCoord =
        getIsoCoord(x, y, currentSprite.getHeight(), currentSprite.getWidth());
    gc.drawImage(currentSprite, rendCoord.getX(), rendCoord.getY());

    // render marker for entity
    if (e.getClientId() != clientID && !e.isMipsman()) {
      return;
    }

    Image marker = (e.isMipsman()) ? r.getMipMarker() : r.getMClientMarker();
    Point2D.Double coord =
        getIsoCoord(x, y, marker.getHeight() + currentSprite.getHeight(), marker.getWidth());

    gc.drawImage(marker, coord.getX(), coord.getY());
  }

  /**
   * @return The top right corner coordinate to start rendering game map from
   */
  private Point2D.Double getMapRenderingCorner() {
    return new Point2D.Double(this.xResolution / (double) 2, this.yResolution / (double) 10);
    // return new Point2D.Double(getIsoCoord(0,map),getIsoCoord(0,0,tileSizeY).getY())
  }

  /**
   * @param entities playable entities to get their scores
   */
  private void renderHUD(Entity[] entities) {
    gc.setFill(Color.WHITE);
    final double paddingRatio = 0.1;
    final double offset = paddingRatio * yResolution;
    double nameScoreGap = yResolution * paddingRatio;

    // calculate corner coordinate to render other players scores from
    Point2D.Double topLeft = new Double(offset, offset);
    Point2D.Double topRight = new Double(xResolution - offset, offset);
    Point2D.Double botLeft = new Double(offset, yResolution - offset - nameScoreGap);
    Point2D.Double botRight = new Double(xResolution - offset, yResolution - offset - nameScoreGap);

    ArrayList<Point2D.Double> scoreCoord =
        new ArrayList<>(Arrays.asList(topLeft, topRight, botLeft, botRight));

    //calculate number of other players
    Entity[] otherPlayers = new Entity[entities.length - 1];
    Entity self = null;
    int playerCounter = 0;
    for (Entity e : entities) {
      if (e.getClientId() != clientID) {
        otherPlayers[playerCounter] = e;
        playerCounter++;
      } else {
        self = e;
      }
    }

    // render own score
    gc.setTextAlign(TextAlignment.CENTER);
    gc.setFont(geoLarge);
    gc.fillText("Score:" + self.getScore(), xResolution / 2, yResolution / 13);

    // render other players scores
    for (int i = 0; i < otherPlayers.length; i++) {
      if ((i % 2 == 0)) {
        gc.setTextAlign(TextAlignment.LEFT);
      } else {
        gc.setTextAlign(TextAlignment.RIGHT);
      }
      Point2D.Double cornerCoord = scoreCoord.get(i);
      gc.setFont(geoSmall);
      gc.fillText(
          "Score:" + otherPlayers[i].getScore(),
          cornerCoord.getX(),
          cornerCoord.getY() + nameScoreGap);
      gc.setFont(geoLarge);
      gc.fillText("Player" + otherPlayers[i].getClientId(), cornerCoord.getX(), cornerCoord.getY());
    }
  }

  /**
   * @param colour sets Graphics context fill colour using an intRGB (gc.setFillColour only allows
   * setting of colour objects)
   */
  private void setFillColour(int colour) {
    gc.setFill(
        new Color(
            (colour >> 16 & 0xFF) / (double) 255,
            (colour >> 8 & 0xFF) / (double) 255,
            (colour & 0xFF) / (double) 255,
            1));
  }

  /**
   * sets new resolution for the renderer and re initialises assets with the new resolution
   *
   * @param x new x resolution
   * @param y new y resolution
   * @param mode scaling mode
   */
  public void setResolution(int x, int y, RenderingMode mode) {
    r.setResolution(x, y, mode);
    xResolution = x;
    yResolution = y;
    this.init();
  }
}
