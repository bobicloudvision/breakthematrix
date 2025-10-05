import { positionsBox } from './helpers/positions';

class LineRenderer {
    constructor(data) {
        this._data = data;
        this._cachedGroups = null;
        this._lastLineCount = 0;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => {
            const ctx = scope.context;
            const hRatio = scope.horizontalPixelRatio;
            const vRatio = scope.verticalPixelRatio;

            // Cache line groups if lines haven't changed
            if (!this._cachedGroups || this._lastLineCount !== this._data.lines.length) {
                this._buildLineGroups();
                this._lastLineCount = this._data.lines.length;
            }

            // Draw lines by style group using cached groups
            this._cachedGroups.groups.forEach((group) => {
                ctx.strokeStyle = group.color;
                ctx.lineWidth = group.lineWidth;
                
                // Set line dash pattern once per group
                if (group.lineStyle === 'dashed') {
                    ctx.setLineDash([5 * hRatio, 5 * hRatio]);
                } else if (group.lineStyle === 'dotted') {
                    ctx.setLineDash([2 * hRatio, 2 * hRatio]);
                } else {
                    ctx.setLineDash([]);
                }
                
                // Draw all lines in this group in one path
                ctx.beginPath();
                const lines = group.lines;
                for (let i = 0; i < lines.length; i++) {
                    const line = lines[i];
                    ctx.moveTo(line.x1 * hRatio, line.y1 * vRatio);
                    ctx.lineTo(line.x2 * hRatio, line.y2 * vRatio);
                }
                ctx.stroke();
            });

            // Reset line dash
            ctx.setLineDash([]);

            // Draw labels if any
            const labels = this._cachedGroups.labels;
            if (labels.length > 0) {
                ctx.font = `${10 * vRatio}px Arial`;
                ctx.textBaseline = 'bottom';
                ctx.shadowColor = 'rgba(0, 0, 0, 0.8)';
                ctx.shadowBlur = 2 * vRatio;
                
                for (let i = 0; i < labels.length; i++) {
                    const label = labels[i];
                    ctx.fillStyle = label.color;
                    ctx.fillText(label.text, label.x * hRatio, label.y * vRatio);
                }
                
                ctx.shadowBlur = 0;
            }
        });
    }

    _buildLineGroups() {
        const lineGroups = new Map();
        const labels = [];
        
        const lines = this._data.lines;
        for (let i = 0; i < lines.length; i++) {
            const line = lines[i];
            
            const color = line.color || '#2196F3';
            const lineWidth = line.lineWidth || 1;
            const lineStyle = line.lineStyle || 'solid';
            const styleKey = `${color}_${lineWidth}_${lineStyle}`;

            // Group lines by style
            let group = lineGroups.get(styleKey);
            if (!group) {
                group = {
                    color,
                    lineWidth,
                    lineStyle,
                    lines: []
                };
                lineGroups.set(styleKey, group);
            }
            
            group.lines.push({ 
                x1: line.x1, 
                y1: line.y1, 
                x2: line.x2, 
                y2: line.y2 
            });

            // Collect labels
            if (line.label) {
                labels.push({
                    text: line.label,
                    x: line.x1 + 4,
                    y: line.y1 - 4,
                    color: color
                });
            }
        }

        this._cachedGroups = {
            groups: Array.from(lineGroups.values()),
            labels: labels
        };
    }
}

class LinePaneView {
    constructor(source) {
        this._source = source;
        this._lines = []; // Initialize empty array
    }

    /**
     * Normalize time value to Unix timestamp (seconds)
     * @param {number|string|Date} time - Time value in various formats
     * @returns {number|null} - Unix timestamp in seconds, or null if invalid
     */
    _normalizeTime(time) {
        // Already a valid Unix timestamp (seconds)
        if (typeof time === 'number' && time > 0 && isFinite(time)) {
            // If it looks like milliseconds (> year 2100 in seconds), convert to seconds
            if (time > 4102444800) { // Jan 1, 2100 in seconds
                return Math.floor(time / 1000);
            }
            return time;
        }
        
        // String: try to parse as ISO date or timestamp
        if (typeof time === 'string') {
            const parsed = Date.parse(time);
            if (!isNaN(parsed)) {
                return Math.floor(parsed / 1000);
            }
        }
        
        // Date object
        if (time instanceof Date) {
            return Math.floor(time.getTime() / 1000);
        }
        
        // Invalid time value
        return null;
    }

    update() {
        const series = this._source._series;
        const timeScale = this._source._chart.timeScale();
        const chartData = this._source._chartData;
        
        // Get visible time range for culling
        const visibleRange = timeScale.getVisibleLogicalRange();
        let visibleTimeRange = null;
        
        if (visibleRange && chartData && chartData.length > 0) {
            const startIdx = Math.max(0, Math.floor(visibleRange.from));
            const endIdx = Math.min(chartData.length - 1, Math.ceil(visibleRange.to));
            
            if (startIdx < chartData.length && endIdx >= 0) {
                visibleTimeRange = {
                    from: chartData[startIdx].time,
                    to: chartData[endIdx].time
                };
            }
        }
        
        // Filter and transform only visible lines
        this._lines = this._source._linesData
            .filter((line) => {
                // Validate line has required properties
                if (!line || line.time1 === undefined || line.time2 === undefined || 
                    line.price1 === undefined || line.price2 === undefined) {
                    console.warn('Skipping line with missing properties:', line);
                    return false;
                }
                
                // Normalize time values to Unix timestamps (seconds)
                line.time1 = this._normalizeTime(line.time1);
                line.time2 = this._normalizeTime(line.time2);
                
                // Skip lines with invalid times
                if (line.time1 === null || line.time2 === null) {
                    console.warn('Skipping line with invalid time values:', line);
                    return false;
                }
                
                // Skip lines outside visible time range (culling optimization)
                if (visibleTimeRange) {
                    // Line is visible if any part overlaps with visible range
                    const lineStart = Math.min(line.time1, line.time2);
                    const lineEnd = Math.max(line.time1, line.time2);
                    
                    // Skip if line is completely before or after visible range
                    if (lineEnd < visibleTimeRange.from || lineStart > visibleTimeRange.to) {
                        return false;
                    }
                }
                return true;
            })
            .map((line) => {
            let x1 = timeScale.timeToCoordinate(line.time1);
            let x2 = timeScale.timeToCoordinate(line.time2);
            const y1 = series.priceToCoordinate(line.price1);
            const y2 = series.priceToCoordinate(line.price2);

            // Fallback: If timeToCoordinate returns null, use logical indices
            if ((x1 === null || x2 === null) && chartData && chartData.length > 0) {
                let logical1 = null;
                let logical2 = null;
                
                // Binary search for better performance with large datasets
                // Find logical indices for the line times
                const findLogicalIndex = (time) => {
                    let left = 0;
                    let right = chartData.length - 1;
                    
                    while (left <= right) {
                        const mid = Math.floor((left + right) / 2);
                        if (chartData[mid].time === time) return mid;
                        if (chartData[mid].time < time) {
                            left = mid + 1;
                        } else {
                            right = mid - 1;
                        }
                    }
                    return left < chartData.length ? left : chartData.length - 1;
                };
                
                logical1 = findLogicalIndex(line.time1);
                logical2 = findLogicalIndex(line.time2);
                
                // Convert logical indices to coordinates
                if (logical1 !== null) {
                    x1 = timeScale.logicalToCoordinate(logical1);
                }
                if (logical2 !== null) {
                    x2 = timeScale.logicalToCoordinate(logical2);
                }
            }

            return {
                x1,
                x2,
                y1,
                y2,
                color: line.color,
                lineWidth: line.lineWidth,
                lineStyle: line.lineStyle,
                label: line.label,
            };
        })
        .filter(line => {
            // Final check: skip lines with null coordinates
            return line.x1 !== null && line.x2 !== null && line.y1 !== null && line.y2 !== null;
        });
    }

    renderer() {
        return new LineRenderer({
            lines: this._lines || [],
        });
    }
}

export class LinePrimitive {
    constructor(chart, series, linesData, chartData) {
        this._chart = chart;
        this._series = series;
        this._linesData = linesData;
        this._chartData = chartData; // Main chart data for logical coordinate fallback
        this._paneViews = [new LinePaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    autoscaleInfo(startTimePoint, endTimePoint) {
        // Include line prices in autoscale
        let minPrice = Infinity;
        let maxPrice = -Infinity;

        this._linesData.forEach(line => {
            const minLinePrice = Math.min(line.price1, line.price2);
            const maxLinePrice = Math.max(line.price1, line.price2);
            if (minLinePrice < minPrice) minPrice = minLinePrice;
            if (maxLinePrice > maxPrice) maxPrice = maxLinePrice;
        });

        if (minPrice === Infinity || maxPrice === -Infinity) {
            return null;
        }

        return {
            priceRange: {
                minValue: minPrice,
                maxValue: maxPrice,
            },
        };
    }
}

