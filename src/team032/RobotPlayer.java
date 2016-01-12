package team032;

import battlecode.common.*;

import java.util.Random;

public class RobotPlayer {

  //Moves with rotation as needed
  public static void moveToLoc (Direction dir, RobotController rc) {
    if (rc.isCoreReady()) {
      // If possible, move in this direction
      try {
        if (rc.canMove(dir)) {
            rc.move(dir);
        } else if (rc.canMove(dir.rotateLeft())) {
            rc.move(dir.rotateLeft());
        } else if (rc.canMove(dir.rotateRight())) {
            rc.move(dir.rotateRight());
        } else if (rc.canMove(dir.rotateLeft().rotateLeft())) {
            rc.move(dir.rotateLeft().rotateLeft());
        } else if (rc.canMove(dir.rotateRight().rotateRight())) {
            rc.move(dir.rotateRight().rotateRight());
        }
      } catch (Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
      }

    }
  }

  //Scans for nearby parts
  public static MapLocation findParts(MapLocation rLoc, RobotType rType, RobotController rc) {
    int maxRad = (int)(rType.sensorRadiusSquared / 3.0);
    for (int i = -maxRad; i < maxRad; i++){
      for (int j = -maxRad; j < maxRad; j++){
        MapLocation loc = rLoc.add(i, j);
        if (rc.senseParts(loc) > 0) {
          try {
            //Do signaling
            if (rc.isCoreReady()) {
              if (rType == RobotType.SCOUT) {
                rc.broadcastMessageSignal(loc.x, loc.y, rType.sensorRadiusSquared);
              }
            }
          } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
          }
          return loc;
        }
      }
    }
    return rLoc;
  }

  /**
   * run() is the method that is called when a robot is instantiated in the Battlecode world.
   * If this method returns, the robot dies!
   **/
  @SuppressWarnings("unused")
  public static void run(RobotController rc) {
    // You can instantiate variables here.
    Direction[] directions = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
        Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    RobotType[] robotTypes = {RobotType.SCOUT, RobotType.SOLDIER, RobotType.SOLDIER, RobotType.SOLDIER,
        RobotType.GUARD, RobotType.GUARD, RobotType.VIPER, RobotType.TURRET};
    Random rand = new Random(rc.getID());
    MapLocation myLoc = rc.getLocation();
    RobotType myType = rc.getType();
    int myAttackRange = 0;
    Team myTeam = rc.getTeam();
    Team enemyTeam = myTeam.opponent();

    if (myType == RobotType.ARCHON) {
      while (true) {
        //Get at beginning of each turn...
        myLoc = rc.getLocation();

        try {
          //Build as first priority
          if (rc.isCoreReady()) {
            int typeRoll = rand.nextInt(100);
            RobotType typeToBuild = null;
            if (typeRoll < 15) {
              typeToBuild = RobotType.SCOUT;
            } else if (typeRoll < 35) {
              typeToBuild = RobotType.GUARD;
            } else {
              typeToBuild = RobotType.SOLDIER;
            }

            if (rc.hasBuildRequirements(typeToBuild)) {
              // Choose a random direction to try to build in
              Direction dirToBuild = directions[rand.nextInt(8)];
              for (int i = 0; i < 8; i++) {
                // If possible, build in this direction
                if (rc.canBuild(dirToBuild, typeToBuild)) {
                  rc.build(dirToBuild, typeToBuild);
                  break;
                } else {
                  // Rotate the direction to try
                  dirToBuild = dirToBuild.rotateLeft();
                }
              }
            } else {
              // Choose a direction to try to move in
              MapLocation partsLoc = findParts(myLoc, RobotType.ARCHON, rc);
              Direction partsDir = myLoc.directionTo(partsLoc);
              Direction moveDir = null;
              RobotInfo[] enemies = rc.senseHostileRobots(myLoc, myType.sensorRadiusSquared);

              if (enemies.length != 0) {
                //Signal enemy found
                //rc.broadcastMessageSignal(enemies[0].location.x, enemies[0].location.y, myType.sensorRadiusSquared);

                moveDir = enemies[0].location.directionTo(myLoc);
              } else {
                if (partsLoc != myLoc) {
                  moveDir = partsDir;
                } else {
                  // Read signals for parts info...
                  Signal partSig = rc.readSignal();

                  if (partSig != null) {
                    int[] msg = partSig.getMessage();
                    if (msg != null) { //ARCHON/SCOUT message
                      MapLocation moveTo =  new MapLocation(msg[0], msg[1]);
                      moveDir = myLoc.directionTo(moveTo);
                    } else { //Other message
                      moveDir = myLoc.directionTo(partSig.getLocation());
                    }
                    rc.emptySignalQueue();
                  } else {
                    // Move Randomly
                    moveDir = directions[rand.nextInt(8)];

                  }
                }
              }

              //Move
              if (rc.canMove(moveDir)){
                moveToLoc(moveDir, rc);
              } else if (rc.senseRubble(myLoc.add(moveDir)) > GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
                if (rc.isCoreReady()) {
                  rc.clearRubble(moveDir);
                }
              }
              //Signal disperse
              rc.broadcastMessageSignal(-1, -1, (int)(myType.sensorRadiusSquared / 3));
            }
          }

          Clock.yield();
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    } else if (myType == RobotType.SOLDIER || myType == RobotType.SCOUT || myType == RobotType.GUARD) {
      try {
        // Any code here gets executed exactly once at the beginning of the game.
        myAttackRange = myType.attackRadiusSquared;
      } catch (Exception e) {
        // Throwing an uncaught exception makes the robot die, so we need to catch exceptions.
        // Caught exceptions will result in a bytecode penalty.
        System.out.println(e.getMessage());
        e.printStackTrace();
      }

      while (true) {
        // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
        // at the end of it, the loop will iterate once per game round.
        try {

          int fate = rand.nextInt(1000);
          boolean shouldAttack = false;

          if (rc.isCoreReady()) {
            // If this robot type can attack, check for enemies within range and attack one
            if (myAttackRange > 0) {
              RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
              RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);

              if (enemiesWithinRange.length > 0) {
                shouldAttack = true;
                // Check if weapon is ready
                if (rc.isWeaponReady()) {
                  rc.attackLocation(enemiesWithinRange[rand.nextInt(enemiesWithinRange.length)].location);
                }
              } else if (zombiesWithinRange.length > 0) {
                shouldAttack = true;
                // Check if weapon is ready
                if (rc.isWeaponReady()) {
                  rc.attackLocation(zombiesWithinRange[rand.nextInt(zombiesWithinRange.length)].location);
                }
              }
            }
          }


          if (!shouldAttack) {
            if (rc.isCoreReady()) {
              RobotInfo[] enemies = rc.senseHostileRobots(myLoc, myType.sensorRadiusSquared);
              Direction moveDir = null;
              boolean disperseMode = false;

              //Read signal and move
              Signal moveSig = rc.readSignal();
              if (moveSig != null) {
                int[] msg = moveSig.getMessage();
                if (msg != null) {
                  if (msg[0] == -1 && msg[1] == -1) {
                    //Disperse from signal origin
                    MapLocation disperseFrom =  moveSig.getLocation();
                    moveDir = disperseFrom.directionTo(myLoc);
                    disperseMode = true;
                    //System.out.println("DISPERSE!");
                  } else { //Move towards signal
                    MapLocation moveTo =  new MapLocation(msg[0], msg[1]);
                    moveDir = myLoc.directionTo(moveTo);
                    //Chain along the message
                    rc.broadcastSignal(myType.sensorRadiusSquared);
                  }
                } else {
                  moveDir = myLoc.directionTo(moveSig.getLocation());
                  //Chain along the message
                  //rc.broadcastSignal(myType.sensorRadiusSquared);
                }
                rc.emptySignalQueue();
              }

              if (myType == RobotType.SCOUT && rc.isCoreReady() && !disperseMode) {
                MapLocation partsLoc = findParts(myLoc, myType, rc);
              }

              if (enemies.length > 0) {
                if (myAttackRange > 0) {
                  moveDir = myLoc.directionTo(enemies[0].location);
                  //Signal enemy found
                  rc.broadcastSignal(myType.sensorRadiusSquared);
                } else {
                  moveDir = enemies[0].location.directionTo(myLoc);
                }
              }

              // Choose a random direction to try to move in
              if (moveDir == null) {
                moveDir = directions[fate % 8];
              }
              // Check the rubble in that direction
              if (rc.senseRubble(rc.getLocation().add(moveDir)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
                // Too much rubble, so I should clear it
                if (rc.isCoreReady()) {
                  rc.clearRubble(moveDir);
                }
                // Check if I can move in this direction
              } else if (rc.canMove(moveDir)) {
                // Move
                moveToLoc(moveDir, rc);
              }
            }
          }

          Clock.yield();
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    } else if (rc.getType() == RobotType.TURRET) {
      try {
        myAttackRange = rc.getType().attackRadiusSquared;
      } catch (Exception e) {
        System.out.println(e.getMessage());
        e.printStackTrace();
      }

      while (true) {
        // This is a loop to prevent the run() method from returning. Because of the Clock.yield()
        // at the end of it, the loop will iterate once per game round.
        try {
          // If this robot type can attack, check for enemies within range and attack one
          if (rc.isWeaponReady()) {
            RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, enemyTeam);
            RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);
            if (enemiesWithinRange.length > 0) {
              for (RobotInfo enemy : enemiesWithinRange) {
                // Check whether the enemy is in a valid attack range (turrets have a minimum range)
                if (rc.canAttackLocation(enemy.location)) {
                  rc.attackLocation(enemy.location);
                  break;
                }
              }
            } else if (zombiesWithinRange.length > 0) {
              for (RobotInfo zombie : zombiesWithinRange) {
                if (rc.canAttackLocation(zombie.location)) {
                  rc.attackLocation(zombie.location);
                  break;
                }
              }
            }
          }

          Clock.yield();
        } catch (Exception e) {
          System.out.println(e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }
}
