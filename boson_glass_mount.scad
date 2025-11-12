/*
 * Google Glass EE2 + FLIR Boson 320 Mount
 * Parametric OpenSCAD design
 * 
 * This design creates a top-mount bracket that attaches to the Glass frame
 * and holds the Boson 320 thermal camera with lens
 */

// ==============================
// PARAMETERS - Adjust these as needed
// ==============================

// Boson 320 Core dimensions
boson_body_size = 21;  // 21mm cube
boson_tolerance = 0.5;  // Clearance around Boson
boson_lens_diameter = 12;  // Diameter of your lens (adjust based on actual)
boson_lens_length = 15;  // Length your lens protrudes

// Glass frame dimensions (measure your actual device)
frame_thickness = 5;  // Thickness of Glass brow bar
frame_width = 60;  // Width of mounting area
frame_height = 8;  // Height of frame bar

// Mount design parameters
wall_thickness = 2.5;  // Wall thickness for strength
arm_length = 50;  // Height from frame to camera
arm_width = 18;  // Width of support arm
cable_channel_width = 6;  // Width for USB-C cable
tilt_angle = 5;  // Downward angle in degrees

// Ventilation
vent_slot_width = 2;
vent_slot_length = 15;
vent_spacing = 4;

// ==============================
// CALCULATED VALUES
// ==============================

boson_cavity_size = boson_body_size + boson_tolerance;
lens_opening_radius = boson_lens_diameter / 2 + 0.5;

// ==============================
// MAIN ASSEMBLY
// ==============================

module complete_mount() {
    union() {
        // Frame clamp (bottom)
        translate([0, 0, 0])
            frame_clamp();
        
        // Support arm (middle)
        translate([0, frame_thickness/2 + wall_thickness, frame_height/2])
            support_arm();
        
        // Boson cage (top)
        translate([0, frame_thickness/2 + wall_thickness + arm_width/2, arm_length + frame_height/2])
            rotate([tilt_angle, 0, 0])
                boson_cage();
    }
}

// ==============================
// COMPONENTS
// ==============================

module frame_clamp() {
    /*
     * C-shaped clamp that wraps around Glass frame
     * Snap-fit design for easy installation/removal
     */
    
    difference() {
        // Outer shell
        cube([frame_width, frame_thickness + 2*wall_thickness, frame_height + 2*wall_thickness], center=true);
        
        // Frame cavity (slightly oversized for fit)
        cube([frame_width - 2*wall_thickness, frame_thickness + 0.2, frame_height + 0.2], center=true);
        
        // Opening at bottom for snap-fit
        translate([0, 0, -(frame_height + 2*wall_thickness)/2 - 0.5])
            cube([frame_width - 8, frame_thickness + 0.2, wall_thickness + 1], center=true);
    }
    
    // Add grip ridges inside for friction
    for (i = [-2:2]) {
        translate([i * 10, frame_thickness/2 + 0.1, 0])
            cube([1, 0.3, frame_height], center=true);
    }
}

module support_arm() {
    /*
     * Vertical arm connecting frame clamp to camera cage
     * Includes cable channel for USB-C routing
     */
    
    difference() {
        // Main arm body
        translate([0, 0, arm_length/2])
            cube([arm_width, arm_width, arm_length], center=true);
        
        // Cable channel (runs up the back)
        translate([0, arm_width/2 - cable_channel_width/2, cable_channel_width/2])
            cube([cable_channel_width, cable_channel_width + 1, arm_length + 10], center=true);
        
        // Strain relief curve at bottom
        translate([0, arm_width/2 - cable_channel_width/2, 0])
            rotate([0, 90, 0])
                cylinder(h=cable_channel_width + 1, r=3, center=true, $fn=20);
    }
    
    // Reinforcement ribs on sides
    for (side = [-1, 1]) {
        translate([side * arm_width/2, 0, arm_length/2])
            rotate([0, 0, 0])
                linear_extrude(height=arm_length)
                    polygon([[0, 0], [0, arm_width/2], [wall_thickness, 0]]);
    }
}

module boson_cage() {
    /*
     * Cage that holds Boson 320 securely
     * Includes lens opening, ventilation, and spring clip retention
     */
    
    difference() {
        // Outer shell
        cube([boson_cavity_size + 2*wall_thickness, 
              boson_cavity_size + 2*wall_thickness, 
              boson_cavity_size + 2*wall_thickness], 
             center=true);
        
        // Boson cavity
        cube([boson_cavity_size, boson_cavity_size, boson_cavity_size], center=true);
        
        // Front opening for lens
        translate([0, (boson_cavity_size + 2*wall_thickness)/2, 0])
            rotate([90, 0, 0])
                cylinder(h=wall_thickness + 2, r=lens_opening_radius, center=true, $fn=40);
        
        // Back opening for USB-C connector
        translate([0, -(boson_cavity_size + 2*wall_thickness)/2, -boson_cavity_size/4])
            cube([10, wall_thickness + 2, 6], center=true);
        
        // Top ventilation slots
        for (i = [-1, 1]) {
            translate([i * vent_spacing, 0, (boson_cavity_size + 2*wall_thickness)/2])
                cube([vent_slot_width, vent_slot_length, wall_thickness + 2], center=true);
        }
        
        // Bottom ventilation slots  
        for (i = [-1, 1]) {
            translate([i * vent_spacing, 0, -(boson_cavity_size + 2*wall_thickness)/2])
                cube([vent_slot_width, vent_slot_length, wall_thickness + 2], center=true);
        }
        
        // Side access slots for spring clips
        for (side = [-1, 1]) {
            translate([side * (boson_cavity_size + 2*wall_thickness)/2, 0, 0])
                cube([wall_thickness + 2, boson_cavity_size - 4, 2], center=true);
        }
    }
    
    // Lens hood (extends forward)
    translate([0, (boson_cavity_size + 2*wall_thickness)/2 + boson_lens_length/2, 0])
        difference() {
            cylinder(h=boson_lens_length, r=lens_opening_radius + 2, center=true, $fn=40);
            cylinder(h=boson_lens_length + 2, r=lens_opening_radius, center=true, $fn=40);
        }
    
    // Spring clip retention features (flexible tabs)
    for (side = [-1, 1]) {
        translate([side * (boson_cavity_size/2 + wall_thickness + 0.5), 0, 0])
            spring_clip(side);
    }
}

module spring_clip(side) {
    /*
     * Flexible retention clip for holding Boson
     * Print these in a flexible orientation for springiness
     */
    
    linear_extrude(height=15, center=true)
        polygon([
            [side * 0, 0],
            [side * 0, 3],
            [side * 1, 2.5],
            [side * 2, 1.5],
            [side * 2, 0]
        ]);
}

// ==============================
// CABLE MANAGEMENT ACCESSORIES
// ==============================

module cable_clip() {
    /*
     * Small clip for routing cable along arm
     * Print several of these
     */
    
    difference() {
        union() {
            // Base
            cube([10, 8, 2], center=true);
            // Clip arc
            translate([0, 0, 1])
                rotate([0, 0, 0])
                    difference() {
                        cylinder(h=6, r=4, center=true, $fn=30);
                        cylinder(h=7, r=3, center=true, $fn=30);
                        translate([0, -5, 0])
                            cube([10, 10, 10], center=true);
                    }
        }
        // Mounting hole
        cylinder(h=5, r=1.5, center=true, $fn=20);
    }
}

// ==============================
// RENDERING
// ==============================

// Render the complete mount
complete_mount();

// Optionally render cable clips separately
// Uncomment to generate clips for printing:
// for (i = [0:3]) {
//     translate([0, -50 - i*20, 0])
//         cable_clip();
// }

// ==============================
// PRINT INSTRUCTIONS
// ==============================

/*
 * RECOMMENDED PRINT SETTINGS:
 * 
 * Material: PETG (best for this application)
 *   - Layer height: 0.2mm
 *   - Wall thickness: 4 perimeters
 *   - Infill: 30% gyroid
 *   - Print temperature: 230-240°C
 *   - Bed temperature: 70-80°C
 * 
 * Alternative: ABS (stronger, harder to print)
 * Alternative: PLA (easiest, but less durable)
 * 
 * For frame contact surfaces: Add TPU pads after printing
 * 
 * ORIENTATION:
 * - Print with frame clamp on build plate
 * - Boson cage should be upright
 * - Minimal supports should be needed if designed correctly
 * 
 * POST-PROCESSING:
 * - Test fit on Glass frame before assembling Boson
 * - Sand any rough edges
 * - Add thin rubber/silicone pads to frame contact points
 * - Consider painting with heat-resistant paint
 */

// ==============================
// CUSTOMIZATION NOTES
// ==============================

/*
 * TO CUSTOMIZE FOR YOUR SETUP:
 * 
 * 1. Measure your Glass frame precisely with calipers:
 *    - Update frame_thickness, frame_width, frame_height
 * 
 * 2. Measure your specific lens:
 *    - Update boson_lens_diameter and boson_lens_length
 * 
 * 3. Adjust mounting position:
 *    - Change arm_length to raise/lower camera
 *    - Change tilt_angle for camera angle
 * 
 * 4. Test fit with cardboard prototype first!
 * 
 * 5. Print iteratively:
 *    - Start with just frame_clamp() to test fit
 *    - Then add arm
 *    - Finally add cage
 * 
 * FUTURE ENHANCEMENTS:
 * - Add hinge for tilt adjustment
 * - Add quick-release mechanism
 * - Add second camera mount for visual camera
 */
